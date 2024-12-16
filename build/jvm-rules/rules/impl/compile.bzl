# Copyright 2018 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
load(
    "@rules_java//java:defs.bzl",
    "JavaInfo",
    "java_common",
)
load(
    "@rules_kotlin//kotlin/internal:defs.bzl",
    _JAVA_RUNTIME_TOOLCHAIN_TYPE = "JAVA_RUNTIME_TOOLCHAIN_TYPE",
    _JAVA_TOOLCHAIN_TYPE = "JAVA_TOOLCHAIN_TYPE",
    _KtCompilerPluginInfo = "KtCompilerPluginInfo",
    _KtJvmInfo = "KtJvmInfo",
    _KtPluginConfiguration = "KtPluginConfiguration",
    _TOOLCHAIN_TYPE = "TOOLCHAIN_TYPE",
)
load(
    "@rules_kotlin//kotlin/internal:opts.bzl",
    "JavacOptions",
    "javac_options_to_flags",
)
load(
    "@rules_kotlin//kotlin/internal/jvm:associates.bzl",
    _associate_utils = "associate_utils",
)
load(
    "@rules_kotlin//kotlin/internal/jvm:plugins.bzl",
    "is_ksp_processor_generating_java",
    _plugin_mappers = "mappers",
)
load(
    "@rules_kotlin//kotlin/internal/utils:sets.bzl",
    _sets = "sets",
)
load(
    "@rules_kotlin//kotlin/internal/utils:utils.bzl",
    _utils = "utils",
)
load(
    "//:rules/impl/builder-args.bzl",
    "init_builder_args",
)
load(
    "//:rules/impl/kotlinc-options.bzl",
    "kotlinc_options_to_flags",
    "KotlincOptions"
)

# UTILITY ##############################################################################################################
def find_java_toolchain(ctx, target):
    if _JAVA_TOOLCHAIN_TYPE in ctx.toolchains:
        return ctx.toolchains[_JAVA_TOOLCHAIN_TYPE].java
    return target[java_common.JavaToolchainInfo]

def find_java_runtime_toolchain(ctx, target):
    if _JAVA_RUNTIME_TOOLCHAIN_TYPE in ctx.toolchains:
        return ctx.toolchains[_JAVA_RUNTIME_TOOLCHAIN_TYPE].java_runtime
    return target[java_common.JavaRuntimeInfo]

def _java_info(target):
    return target[JavaInfo] if JavaInfo in target else None

def _deps_artifacts(toolchain, targets):
    """Collect Jdeps artifacts if required."""
    deps_artifacts = [t[JavaInfo].outputs.jdeps for t in targets if JavaInfo in t and t[JavaInfo].outputs.jdeps] if toolchain.experimental_report_unused_deps else []
    return depset(deps_artifacts)

def _partitioned_srcs(srcs):
    """Creates a struct of srcs sorted by extension. Fails if there are no sources."""
    kt_srcs = []
    java_srcs = []
    src_jars = []

    for f in srcs:
        if f.path.endswith(".kt"):
            kt_srcs.append(f)
        elif f.path.endswith(".java"):
            java_srcs.append(f)
        elif f.path.endswith(".srcjar"):
            src_jars.append(f)

    return struct(
        kt = kt_srcs,
        java = java_srcs,
        all_srcs = kt_srcs + java_srcs,
        src_jars = src_jars,
    )

def _compiler_toolchains(ctx):
    """Creates a struct of the relevant compilation toolchains"""
    return struct(
        kt = ctx.toolchains[_TOOLCHAIN_TYPE],
        java = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        java_runtime = find_java_runtime_toolchain(ctx, ctx.attr._host_javabase),
    )

def _compute_transitive_jars(dep_infos, prune_transitive_deps):
    compile_jars = [d.compile_jars for d in dep_infos]
    if prune_transitive_deps:
        return compile_jars

    transitive_compile_time_jars = [d.transitive_compile_time_jars for d in dep_infos]
    return compile_jars + transitive_compile_time_jars

def _jvm_deps(ctx, toolchains, associated_targets, deps, runtime_deps = []):
    """Encapsulates jvm dependency metadata."""
    diff = _sets.intersection(
        _sets.copy_of([x.label for x in associated_targets]),
        _sets.copy_of([x.label for x in deps]),
    )
    if diff:
        fail(
            "\n------\nTargets should only be put in associates= or deps=, not both:\n%s" %
            ",\n ".join(["    %s" % x for x in list(diff)]),
        )
    dep_infos = [_java_info(d) for d in associated_targets + deps] + [toolchains.kt.jvm_stdlibs]

    # reduced classpath, exclude transitive deps from compilation
    prune_transitive_deps = toolchains.kt.experimental_prune_transitive_deps and "kt_experimental_prune_transitive_deps_incompatible" not in ctx.attr.tags
    transitive = _compute_transitive_jars(dep_infos, prune_transitive_deps)

    return struct(
        deps = dep_infos,
        compile_jars = depset(
            transitive = transitive,
        ),
        runtime_deps = [_java_info(d) for d in runtime_deps],
    )

def _java_infos_to_compile_jars(java_infos):
    return depset(transitive = [j.compile_jars for j in java_infos])

def _exported_plugins(deps):
    """Encapsulates compiler dependency metadata."""
    plugins = []
    for dep in deps:
        if _KtJvmInfo in dep and dep[_KtJvmInfo] != None:
            plugins.extend(dep[_KtJvmInfo].exported_compiler_plugins.to_list())
    return plugins

def _collect_plugins_for_export(local, exports):
    """Collects into a depset. """
    return depset(
        local,
        transitive = [
            e[_KtJvmInfo].exported_compiler_plugins
            for e in exports
            if _KtJvmInfo in e and e[_KtJvmInfo]
        ],
    )

def _format_compile_plugin_options(o):
    """Format compiler option into id:value for cmd line."""
    return [
        "%s:%s" % (o.id, o.value),
    ]

def _new_plugins_from(targets):
    """Returns a struct containing the plugin metadata for the given targets.

    Args:
        targets: A list of targets.
    Returns:
        A struct containing the plugins for the given targets in the format:
        {
            stubs_phase = {
                classpath = depset,
                options= List[KtCompilerPluginOption],
            ),
            compile = {
                classpath = depset,
                options = List[KtCompilerPluginOption],
            },
        }
    """

    all_plugins = {}
    plugins_without_phase = []
    for t in targets:
        if _KtCompilerPluginInfo not in t:
            continue
        plugin = t[_KtCompilerPluginInfo]
        if not (plugin.stubs or plugin.compile):
            plugins_without_phase.append("%s: %s" % (t.label, plugin.id))
        if plugin.id in all_plugins:
            # This need a more robust error messaging.
            fail("has multiple plugins with the same id: %s." % plugin.id)
        all_plugins[plugin.id] = plugin

    if plugins_without_phase:
        fail("has plugin without a phase defined: %s" % cfgs_without_plugin)

    all_plugin_cfgs = {}
    cfgs_without_plugin = []
    for t in targets:
        if _KtPluginConfiguration not in t:
            continue
        cfg = t[_KtPluginConfiguration]
        if cfg.id not in all_plugins:
            cfgs_without_plugin.append("%s: %s" % (t.label, cfg.id))
        all_plugin_cfgs[cfg.id] = cfg

    if cfgs_without_plugin:
        fail("has plugin configurations without corresponding plugins: %s" % cfgs_without_plugin)

    return struct(
        stubs_phase = _new_plugin_from(all_plugin_cfgs, [p for p in all_plugins.values() if p.stubs]),
        compile_phase = _new_plugin_from(all_plugin_cfgs, [p for p in all_plugins.values() if p.compile]),
    )

def _new_plugin_from(all_cfgs, plugins_for_phase):
    classpath = []
    data = []
    options = []
    for p in plugins_for_phase:
        classpath.append(p.classpath)
        options.extend(p.options)
        if p.id in all_cfgs:
            cfg = all_cfgs[p.id]
            classpath.append(cfg.classpath)
            data.append(cfg.data)
            options.extend(cfg.options)

    return struct(
        classpath = depset(transitive = classpath),
        data = depset(transitive = data),
        options = options,
    )

# INTERNAL ACTIONS #####################################################################################################
def _fold_jars_action(ctx, rule_kind, toolchains, output_jar, input_jars, action_type = ""):
    """Set up an action to Fold the input jars into a normalized output jar."""
    args = ctx.actions.args()
    args.add_all([
        "--normalize",
        "--exclude_build_data",
        "--add_missing_directories",
    ])
    args.add_all([
        "--deploy_manifest_lines",
        "Target-Label: %s" % str(ctx.label),
        "Injecting-Rule-Kind: %s" % rule_kind,
    ])
    args.add("--output", output_jar)
    args.add_all(input_jars, before_each = "--sources")
    ctx.actions.run(
        mnemonic = "KotlinFoldJars" + action_type,
        inputs = input_jars,
        outputs = [output_jar],
        executable = toolchains.java.single_jar,
        arguments = [args],
        progress_message = "Merging Kotlin output jar %%{label}%s from %d inputs" % (
            "" if not action_type else " (%s)" % action_type,
            len(input_jars),
        ),
    )

def _run_merge_jdeps_action(ctx, toolchain, jdeps, output, deps):
    """Creates a Jdeps merger action invocation."""

    mnemonic = "JdepsMerge"
    progress_message = "%s %%{label} { jdeps: %d }" % (
        mnemonic,
        len(jdeps),
    )

    inputs = depset(jdeps)
    if not toolchain.experimental_report_unused_deps == "off":
        # for sandboxing to work, and for this action to be deterministic, the compile jars need to be passed as inputs
        inputs = depset(jdeps, transitive = [depset([], transitive = [dep.transitive_compile_time_jars for dep in deps])])

    ctx.actions.run(
        mnemonic = mnemonic,
        inputs = inputs,
        outputs = [output],
        use_default_shell_env = True,
        executable = ctx.attr._jdeps_merger.files_to_run.executable,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
        },
        arguments = ["--flagfile=|jdeps|" + output.path + "|" + str(ctx.label) + "|" + toolchain.experimental_report_unused_deps],
        progress_message = progress_message,
    )

def _run_ksp_builder_actions(
        ctx,
        rule_kind,
        toolchains,
        srcs,
        associates,
        compile_deps,
        deps_artifacts,
        annotation_processors,
        transitive_runtime_jars,
        plugins):
    """Runs KSP using the KotlinBuilder tool

    Returns:
        A struct containing KSP outputs
    """
    ksp_generated_java_srcjar = ctx.actions.declare_file(ctx.label.name + "-ksp-kt-gensrc.jar")

    _run_kt_builder_action(
        ctx = ctx,
        rule_kind = rule_kind,
        toolchains = toolchains,
        srcs = srcs,
        generated_src_jars = [],
        associates = associates,
        compile_deps = compile_deps,
        deps_artifacts = deps_artifacts,
        annotation_processors = annotation_processors,
        transitive_runtime_jars = transitive_runtime_jars,
        plugins = plugins,
        outputs = {
            "ksp_generated_java_srcjar": ksp_generated_java_srcjar,
        },
        build_kotlin = False,
        mnemonic = "KotlinKsp",
    )
    return ksp_generated_java_srcjar

def _run_kt_builder_action(
        ctx,
        rule_kind,
        toolchains,
        srcs,
        generated_src_jars,
        associates,
        compile_deps,
        deps_artifacts,
        annotation_processors,
        transitive_runtime_jars,
        plugins,
        outputs,
        build_kotlin = True,
        mnemonic = "KotlinCompile"):
    """Creates a KotlinBuilder action invocation."""
    kotlinc_options = ctx.attr.kotlinc_opts[KotlincOptions]
    javac_options = ctx.attr.javac_opts[JavacOptions] if ctx.attr.javac_opts else toolchains.kt.javac_options

    args = init_builder_args(ctx, rule_kind, associates.module_name, toolchains.kt)

    for f, path in outputs.items():
        args.add("--" + f, path)

    kotlinc_options_to_flags(kotlinc_options, args)

    args.add_all("--opt_in", kotlinc_options.opt_in)

    #     args.add_all("--javacopts", javac_options_to_flags(javac_options))
    args.add_all("--direct_dependencies", _java_infos_to_compile_jars(compile_deps.deps))
    args.add_all("--classpath", compile_deps.compile_jars)
#     if not toolchains.kt.experimental_reduce_classpath_mode == "NONE":
#         args.add("--reduced_classpath_mode", "true")
    args.add_all("--deps_artifacts", deps_artifacts)
    args.add_all("--kotlin_friend_paths", associates.jars, map_each = _associate_utils.flatten_jars)
    if ctx.coverage_instrumented():
        args.add("--instrument_coverage")

    # collect and prepare plugin descriptor for the worker
    args.add_all(
        "--processors",
        annotation_processors,
        map_each = _plugin_mappers.kt_plugin_to_processor,
        omit_if_empty = True,
        uniquify = True,
    )

    args.add_all(
        "--processor_path",
        annotation_processors,
        map_each = _plugin_mappers.kt_plugin_to_processorpath,
        omit_if_empty = True,
        uniquify = True,
    )

    args.add_all(
        "--stubs_plugin_classpath",
        plugins.stubs_phase.classpath,
        omit_if_empty = True,
    )

    args.add_all(
        "--stubs_plugin_options",
        plugins.stubs_phase.options,
        map_each = _format_compile_plugin_options,
        omit_if_empty = True,
    )

    args.add_all(
        "--compiler_plugin_classpath",
        plugins.compile_phase.classpath,
        omit_if_empty = True,
    )

    args.add_all(
        "--compiler_plugin_options",
        plugins.compile_phase.options,
        map_each = _format_compile_plugin_options,
        omit_if_empty = True,
    )

    if not build_kotlin:
        args.add("--build_kotlin", False)

    progress_message = "%s %%{label} { kt: %d, java: %d, srcjars: %d } for %s" % (
        mnemonic,
        len(srcs.kt),
        len(srcs.java),
        len(srcs.src_jars),
        ctx.var.get("TARGET_CPU", "UNKNOWN CPU"),
    )

    ctx.actions.run(
        mnemonic = mnemonic,
        inputs = depset(
            srcs.all_srcs + srcs.src_jars + generated_src_jars,
            transitive = [
                compile_deps.compile_jars,
                transitive_runtime_jars,
                deps_artifacts,
                plugins.stubs_phase.classpath,
                plugins.compile_phase.classpath,
            ],
        ),
        use_default_shell_env = True,
        outputs = [f for f in outputs.values()],
        executable = ctx.attr._kotlin_builder.files_to_run.executable,
        execution_requirements = {
            "worker-key-mnemonic": mnemonic,
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
        },
        arguments = [args],
        progress_message = progress_message,
        env = {
            "LC_CTYPE": "en_US.UTF-8",
            "REPOSITORY_NAME": _utils.builder_workspace_name(ctx),
        },
    )

# MAIN ACTIONS #########################################################################################################

def kt_jvm_produce_jar_actions(ctx, rule_kind):
    """This macro sets up a compile action for a Kotlin jar.

    Args:
        ctx: Invoking rule ctx, used for attr, actions, and label.
        rule_kind: The rule kind --e.g., `kt_jvm_library`.
    Returns:
        A struct containing the providers JavaInfo (`java`) and `kt` (KtJvmInfo). This struct is not intended to be
        used as a legacy provider -- rather the caller should transform the result.
    """
    toolchains = _compiler_toolchains(ctx)
    srcs = _partitioned_srcs(ctx.files.srcs)
    associates = _associate_utils.get_associates(ctx)
    compile_deps = _jvm_deps(
        ctx,
        toolchains,
        associates.targets,
        deps = ctx.attr.deps,
        runtime_deps = ctx.attr.runtime_deps,
    )

    perTargetPlugins = ctx.attr.plugins if hasattr(ctx.attr, "plugins") else []
    annotation_processors = _plugin_mappers.targets_to_annotation_processors(perTargetPlugins + ctx.attr.deps)
    ksp_annotation_processors = _plugin_mappers.targets_to_ksp_annotation_processors(perTargetPlugins + ctx.attr.deps)
    transitive_runtime_jars = _plugin_mappers.targets_to_transitive_runtime_jars(perTargetPlugins + ctx.attr.deps)
    plugins = _new_plugins_from(perTargetPlugins + _exported_plugins(deps = ctx.attr.deps))

    toolchain = toolchains.kt
    deps_artifacts = _deps_artifacts(toolchain, ctx.attr.deps + associates.targets)

    # merge outputs into final runtime jar
    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")

    outputs_struct = _run_kt_java_builder_actions(
        ctx = ctx,
        output_jar = output_jar,
        rule_kind = rule_kind,
        toolchains = toolchains,
        srcs = srcs,
        generated_ksp_src_jars = [],
        associates = associates,
        compile_deps = compile_deps,
        deps_artifacts = deps_artifacts,
        annotation_processors = annotation_processors,
        ksp_annotation_processors = ksp_annotation_processors,
        transitive_runtime_jars = transitive_runtime_jars,
        plugins = plugins,
    )

    compile_jar = outputs_struct.compile_jar
    generated_src_jars = outputs_struct.generated_src_jars
    annotation_processing = outputs_struct.annotation_processing

    source_jar = java_common.pack_sources(
        ctx.actions,
        output_source_jar = ctx.outputs.srcjar,
        sources = srcs.kt + srcs.java,
        source_jars = srcs.src_jars + generated_src_jars,
        java_toolchain = toolchains.java,
    )

    generated_source_jar = java_common.pack_sources(
        ctx.actions,
        output_source_jar = ctx.actions.declare_file(ctx.label.name + "-gensrc.jar"),
        source_jars = generated_src_jars,
        java_toolchain = toolchains.java,
    ) if generated_src_jars else None

    java_info = JavaInfo(
        output_jar = output_jar,
        compile_jar = compile_jar,
        source_jar = source_jar,
        jdeps = outputs_struct.output_jdeps,
        deps = compile_deps.deps,
        runtime_deps = [_java_info(d) for d in ctx.attr.runtime_deps],
        exports = [_java_info(d) for d in getattr(ctx.attr, "exports", [])],
        neverlink = getattr(ctx.attr, "neverlink", False),
        generated_source_jar = generated_source_jar,
    )

    instrumented_files = coverage_common.instrumented_files_info(
        ctx,
        source_attributes = ["srcs"],
        dependency_attributes = ["deps", "exports", "associates"],
        extensions = ["kt", "java"],
    )

    return struct(
        java = java_info,
        instrumented_files = instrumented_files,
        kt = _KtJvmInfo(
            srcs = ctx.files.srcs,
            module_name = associates.module_name,
            module_jars = associates.jars,
            language_version = toolchain.api_version,
            exported_compiler_plugins = _collect_plugins_for_export(
                getattr(ctx.attr, "exported_compiler_plugins", []),
                getattr(ctx.attr, "exports", []),
            ),
            # intellij aspect needs this
            outputs = struct(
                jdeps = outputs_struct.output_jdeps,
                jars = [struct(
                    class_jar = output_jar,
                    ijar = compile_jar,
                    source_jars = [source_jar],
                )],
            ),
            transitive_compile_time_jars = java_info.transitive_compile_time_jars,
            transitive_source_jars = java_info.transitive_source_jars,
            annotation_processing = annotation_processing,
            additional_generated_source_jars = generated_src_jars,
            all_output_jars = [output_jar],
        ),
    )

def _get_or_create_single_jdeps_output(toolchain, java_infos, ctx, compile_deps):
    jdeps = [java_info.outputs.jdeps for java_info in java_infos if java_info.outputs.jdeps]
    if len(jdeps) == 1:
        return jdeps[0]
    elif jdeps:
        output_jdeps = ctx.actions.declare_file(ctx.label.name + ".jdeps")
        _run_merge_jdeps_action(
            ctx = ctx,
            toolchain = toolchain,
            jdeps = jdeps,
            deps = compile_deps.deps,
            output = output_jdeps,
        )

def _compile_java_sources(ctx, output, srcs, generated_ksp_src_jars, compile_deps, kt_stubs_for_java, toolchains, strict_deps):
    """Compiles Java sources if present, otherwise uses KT ABI jar."""

    javac_options = javac_options_to_flags(
        ctx.attr.javac_opts[JavacOptions] if ctx.attr.javac_opts else toolchains.kt.javac_options,
    )

    if srcs.kt:
        # assemblage consideration for Kotlin annotation processing
        javac_options.append("-proc:none")

    return java_common.compile(
        ctx,
        source_files = srcs.java,
        source_jars = srcs.src_jars + generated_ksp_src_jars,
        output = output,
        deps = compile_deps.deps + ([] if kt_stubs_for_java == None else [kt_stubs_for_java]),
        java_toolchain = toolchains.java,
        plugins = _plugin_mappers.targets_to_annotation_processors_java_plugin_info(ctx.attr.plugins) if hasattr(ctx.attr, "plugins") else [],
        javac_opts = javac_options,
        neverlink = getattr(ctx.attr, "neverlink", False),
        strict_deps = strict_deps,
    )

def _run_kt_java_builder_actions(
        ctx,
        output_jar,
        rule_kind,
        toolchains,
        srcs,
        generated_ksp_src_jars,
        associates,
        compile_deps,
        deps_artifacts,
        annotation_processors,
        ksp_annotation_processors,
        transitive_runtime_jars,
        plugins):
    """Runs the necessary KotlinBuilder and JavaBuilder actions to compile a jar

    Returns:
        A struct containing the a list of output_jars and a struct annotation_processing jars
    """
    has_kt_sources = srcs.kt or srcs.src_jars

    # run KSP
    if has_kt_sources and ksp_annotation_processors:
        ksp_generated_class_jar = _run_ksp_builder_actions(
            ctx,
            rule_kind = rule_kind,
            toolchains = toolchains,
            srcs = srcs,
            associates = associates,
            compile_deps = compile_deps,
            deps_artifacts = deps_artifacts,
            annotation_processors = ksp_annotation_processors,
            transitive_runtime_jars = transitive_runtime_jars,
            plugins = plugins,
        )
        generated_ksp_src_jars.append(ksp_generated_class_jar)

    java_infos = []
    outputs = None
    kt_compile_jar = None

    toolchain = toolchains.kt

    kt_stubs_for_java = None
    kt_output_jar = None
    has_java_sources = srcs.java or srcs.src_jars or (generated_ksp_src_jars and is_ksp_processor_generating_java(ctx.attr.plugins))

    jvm_emit_jdeps = toolchain.jvm_emit_jdeps
    # jvm_emit_jdeps = False

    # build Kotlin
    if has_kt_sources:
        kt_output_jar = ctx.actions.declare_file(ctx.label.name + "-kt.jar") if has_java_sources else output_jar
        if not "kt_abi_plugin_incompatible" in ctx.attr.tags:
            kt_compile_jar = ctx.actions.declare_file(ctx.label.name + ("-kt.abi.jar" if has_java_sources else ".abi.jar"))
            outputs = {
                "output": kt_output_jar,
                "abi_jar": kt_compile_jar,
            }
        else:
            kt_compile_jar = kt_output_jar
            outputs = {
                "output": kt_output_jar,
            }

        kt_jdeps = None
        if jvm_emit_jdeps:
            kt_jdeps = ctx.actions.declare_file(ctx.label.name + "-kt.jdeps")
            outputs["kotlin_output_jdeps"] = kt_jdeps

        _run_kt_builder_action(
            ctx = ctx,
            rule_kind = rule_kind,
            toolchains = toolchains,
            srcs = srcs,
            generated_src_jars = generated_ksp_src_jars,
            associates = associates,
            compile_deps = compile_deps,
            deps_artifacts = deps_artifacts,
            annotation_processors = [],
            transitive_runtime_jars = transitive_runtime_jars,
            plugins = plugins,
            outputs = outputs,
            build_kotlin = True,
            mnemonic = "KotlinCompile",
        )

        if not annotation_processors or not srcs.kt:
            kt_stubs_for_java = JavaInfo(compile_jar = kt_compile_jar, output_jar = kt_output_jar, neverlink = True)

        kt_java_info = JavaInfo(
            output_jar = kt_output_jar,
            compile_jar = kt_compile_jar,
            jdeps = kt_jdeps,
            deps = compile_deps.deps,
            runtime_deps = [d[JavaInfo] for d in ctx.attr.runtime_deps],
            exports = [d[JavaInfo] for d in getattr(ctx.attr, "exports", [])],
            neverlink = getattr(ctx.attr, "neverlink", False),
        )
        java_infos.append(kt_java_info)

    compile_jar = kt_compile_jar
    ap_generated_src_jar = None
    if has_java_sources:
        java_output_jar = output_jar if kt_output_jar == None else ctx.actions.declare_file(ctx.label.name + "-java.jar")

        java_part_java_info = _compile_java_sources(
            ctx = ctx,
            output = java_output_jar,
            srcs = srcs,
            generated_ksp_src_jars = generated_ksp_src_jars,
            compile_deps = compile_deps,
            kt_stubs_for_java = kt_stubs_for_java,
            toolchains = toolchains,
            strict_deps = toolchain.experimental_strict_kotlin_deps,
        )

        java_infos.append(java_part_java_info)
        ap_generated_src_jar = java_part_java_info.annotation_processing.source_jar
        compile_jars = [jars.ijar for jars in java_part_java_info.java_outputs]
        output_jars = [jars.class_jar for jars in java_part_java_info.java_outputs]

        if kt_output_jar == None:
            if not len(output_jars) == 1:
                fail("expect the only output")
            if not len(compile_jars) == 1:
                fail("expect the only compile_jar")
            if not output_jars[0] == java_output_jar:
                fail("java_output is not equal to result")

            compile_jar = compile_jars[0]
        else:
            _fold_jars_action(
                ctx,
                rule_kind = rule_kind,
                toolchains = toolchains,
                output_jar = output_jar,
                action_type = "Runtime",
                input_jars = [kt_output_jar, java_output_jar],
            )

            # merge ABI jars into final compile jar
            compile_jar = ctx.actions.declare_file(ctx.label.name + ".abi.jar")
            _fold_jars_action(
                ctx,
                rule_kind = rule_kind,
                toolchains = toolchains,
                output_jar = compile_jar,
                action_type = "Abi",
                input_jars = compile_jars + [kt_compile_jar],
            )
    elif kt_output_jar == None:
        ctx.actions.symlink(output = output_jar, target_file = toolchain.empty_jar)

    annotation_processing = None
    if annotation_processors:
        outputs_list = [java_info.outputs for java_info in java_infos]
        annotation_processing = _create_annotation_processing(
            annotation_processors = annotation_processors,
            ap_class_jar = [jars.class_jar for outputs in outputs_list for jars in outputs.jars][0],
            ap_source_jar = ap_generated_src_jar,
        )

    return struct(
        compile_jar = compile_jar,
        generated_src_jars = generated_ksp_src_jars,
        annotation_processing = annotation_processing,
        output_jdeps = _get_or_create_single_jdeps_output(toolchain, java_infos, ctx, compile_deps) if jvm_emit_jdeps else None,
    )

def _create_annotation_processing(annotation_processors, ap_class_jar, ap_source_jar):
    """Creates the annotation_processing field for Kt to match what JavaInfo

    The Bazel Plugin IDE logic is based on this assumption in order to locate the Annotation
    Processor generated source code.

    See https://docs.bazel.build/versions/master/skylark/lib/JavaInfo.html#annotation_processing
    """
    if annotation_processors:
        return struct(
            enabled = True,
            class_jar = ap_class_jar,
            source_jar = ap_source_jar,
        )
    return None
