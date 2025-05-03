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

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load(
    "@rules_kotlin//kotlin/internal:defs.bzl",
    _JAVA_TOOLCHAIN_TYPE = "JAVA_TOOLCHAIN_TYPE",
    _KtCompilerPluginInfo = "KtCompilerPluginInfo",
    _KtJvmInfo = "KtJvmInfo",
    _KtPluginConfiguration = "KtPluginConfiguration",
)
load("@rules_kotlin//kotlin/internal:opts.bzl", "JavacOptions")
load("//:rules/common-attrs.bzl", "add_dicts")
load("//:rules/impl/associates.bzl", "get_associates")
load("//:rules/impl/builder-args.bzl", "init_builder_args")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions")

def find_java_toolchain(ctx, target):
    if _JAVA_TOOLCHAIN_TYPE in ctx.toolchains:
        return ctx.toolchains[_JAVA_TOOLCHAIN_TYPE].java
    return target[java_common.JavaToolchainInfo]

def _java_info(target):
    return target[JavaInfo] if JavaInfo in target else None

def _partitioned_srcs(srcs):
    kt_srcs = []
    java_srcs = []

    for f in srcs:
        if f.path.endswith(".kt"):
            kt_srcs.append(f)
        elif f.path.endswith(".java"):
            java_srcs.append(f)

    return struct(
        kt = kt_srcs,
        java = java_srcs,
        all_srcs = kt_srcs + java_srcs,
        src_jars = [],
    )

def _compute_transitive_jars(dep_infos, prune_transitive_deps):
    compile_jars = [d.compile_jars for d in dep_infos]
    if prune_transitive_deps:
        return compile_jars

    transitive_compile_time_jars = [d.transitive_compile_time_jars for d in dep_infos]
    return compile_jars + transitive_compile_time_jars

def _jvm_deps(ctx, associated_targets, deps, runtime_deps):
    """Encapsulates jvm dependency metadata."""
    if not len(associated_targets) == 0:
        deps_dict = {it.label: True for it in deps}
        intersection = [it.label for it in associated_targets if it.label in deps_dict]
        if intersection:
            fail(
                "\n------\nTargets should only be put in associates= or deps=, not both:\n%s" %
                ",\n ".join(["    %s" % x for x in intersection]),
            )

    dep_infos = [_java_info(d) for d in associated_targets + deps]

    # reduced classpath, exclude transitive deps from compilation
    #prune_transitive_deps = toolchains.kt.experimental_prune_transitive_deps and "kt_experimental_prune_transitive_deps_incompatible" not in ctx.attr.tags
    prune_transitive_deps = False and "kt_experimental_prune_transitive_deps_incompatible" not in ctx.attr.tags
    transitive = _compute_transitive_jars(dep_infos, prune_transitive_deps)

    return struct(
        deps = dep_infos,
        compile_jars = depset(transitive = transitive),
        runtime_deps = [_java_info(d) for d in runtime_deps],
    )

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
            fail("has multiple plugins with the same id: %s." % plugin.id)
        all_plugins[plugin.id] = plugin

    if plugins_without_phase:
        fail("has plugin without a phase defined: %s" % cfgs_without_plugin)

    plugin_id_to_configuration = {}
    cfgs_without_plugin = []
    for t in targets:
        if _KtPluginConfiguration not in t:
            continue
        cfg = t[_KtPluginConfiguration]
        if cfg.id not in all_plugins:
            cfgs_without_plugin.append("%s: %s" % (t.label, cfg.id))
        plugin_id_to_configuration[cfg.id] = cfg

    if cfgs_without_plugin:
        fail("has plugin configurations without corresponding plugins: %s" % cfgs_without_plugin)

    return struct(
        stubs_phase = [],
        compile_phase = _new_plugin_from(plugin_id_to_configuration, [p for p in all_plugins.values() if p.compile]),
    )

def _new_plugin_from(plugin_id_to_configuration, plugins_for_phase):
    classpath = {}
    options = {}
    for p in plugins_for_phase:
        if p.id in plugin_id_to_configuration:
            cfg = plugin_id_to_configuration[p.id]
            classpath[p.id] = depset(transitive = p.classpath + cfg.classpath)
            options[p.id] = p.options + cfg.options
        else:
            classpath[p.id] = p.classpath
            if p.options:
                options[p.id] = p.options

    return struct(
        classpath = classpath,
        options = options,
    )

def kt_jvm_produce_jar_actions(ctx):
    """This macro sets up a compile action for a Kotlin jar.

    Args:
        ctx: Invoking rule ctx, used for attr, actions, and label.
    Returns:
        A struct containing the providers JavaInfo (`java`) and `kt` (KtJvmInfo). This struct is not intended to be
        used as a legacy provider -- rather the caller should transform the result.
    """
    srcs = _partitioned_srcs(ctx.files.srcs)
    associates = get_associates(ctx)
    compile_deps = _jvm_deps(
        ctx = ctx,
        associated_targets = associates.targets,
        deps = ctx.attr.deps,
        runtime_deps = ctx.attr.runtime_deps,
    )

    perTargetPlugins = ctx.attr.plugins if hasattr(ctx.attr, "plugins") else []
    plugins = _new_plugins_from(perTargetPlugins + _exported_plugins(deps = ctx.attr.deps))

    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")

    transitiveInputs = [compile_deps.compile_jars]
    _collect_runtime_jars(perTargetPlugins, transitiveInputs)
    _collect_runtime_jars(ctx.attr.deps, transitiveInputs)

    compile_jar = _run_jvm_builder(
        ctx = ctx,
        output_jar = output_jar,
        srcs = srcs,
        associates = associates,
        compile_deps = compile_deps,
        transitiveInputs = transitiveInputs,
        plugins = plugins,
    )

    source_jar = java_common.pack_sources(
        ctx.actions,
        output_source_jar = ctx.outputs.srcjar,
        sources = srcs.kt + srcs.java,
        source_jars = srcs.src_jars,
        java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
    )

    java_info = JavaInfo(
        output_jar = output_jar,
        compile_jar = compile_jar,
        source_jar = source_jar,
        deps = compile_deps.deps,
        runtime_deps = [_java_info(d) for d in ctx.attr.runtime_deps],
        exports = [_java_info(d) for d in getattr(ctx.attr, "exports", [])],
        neverlink = getattr(ctx.attr, "neverlink", False),
    )

    return struct(
        java = java_info,
        kt = _KtJvmInfo(
            srcs = ctx.files.srcs,
            module_name = associates.module_name,
            module_jars = associates.jars,
            exported_compiler_plugins = _collect_plugins_for_export(
                getattr(ctx.attr, "exported_compiler_plugins", []),
                getattr(ctx.attr, "exports", []),
            ),
            # intellij aspect needs this
            outputs = struct(
                jars = [struct(
                    class_jar = output_jar,
                    ijar = compile_jar,
                    source_jars = [source_jar],
                )],
            ),
            transitive_compile_time_jars = java_info.transitive_compile_time_jars,
            transitive_source_jars = java_info.transitive_source_jars,
            all_output_jars = [output_jar],
        ),
    )

def _run_jvm_builder(
        ctx,
        output_jar,
        srcs,
        associates,
        compile_deps,
        transitiveInputs,
        plugins):
    """Runs the necessary JvmBuilder actions to compile a jar

    Returns:
        ABI jar
    """

    kotlin_inc_threshold = ctx.attr._kotlin_inc_threshold[BuildSettingInfo].value
    if kotlin_inc_threshold == -1:
        kotlinc_options = ctx.attr.kotlinc_opts[KotlincOptions]
        kotlin_inc_threshold = kotlinc_options.inc_threshold
    java_inc_threshold = ctx.attr._java_inc_threshold[BuildSettingInfo].value

    args = init_builder_args(ctx, associates, transitiveInputs, plugins = plugins, compile_deps = compile_deps)
    args.add("--out", output_jar)

    outputs = [output_jar]
    abi_jar = output_jar
    abi_jar = ctx.actions.declare_file(ctx.label.name + ".abi.jar")
    outputs.append(abi_jar)
    args.add("--abi-out", abi_jar)

    javac_opts = ctx.attr.javac_opts[JavacOptions] if ctx.attr.javac_opts else None
    if javac_opts and javac_opts.add_exports:
        args.add_all("--add-export", javac_opts.add_exports)

    isIncremental = (kotlin_inc_threshold != -1 and len(srcs.kt) >= kotlin_inc_threshold) or (java_inc_threshold != -1 and len(srcs.java) >= java_inc_threshold)
    if not isIncremental:
        args.add("--non-incremental")

    javaCount = len(srcs.java)
    args.add("--java-count", javaCount)
    ctx.actions.run(
        mnemonic = "JvmCompile",
        env = {
            "MALLOC_ARENA_MAX": "2",
        },
        inputs = depset(srcs.all_srcs, transitive = transitiveInputs),
        use_default_shell_env = True,
        outputs = outputs,
        executable = ctx.attr._jvm_builder.files_to_run.executable,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = [args],
        progress_message = "compile %%{label} (kt: %d, java: %d%s}" % (len(srcs.kt), javaCount, "" if isIncremental else ", non-incremental"),
    )

    return abi_jar

def _collect_runtime_jars(targets, transitive):
    for t in targets:
        if JavaInfo in t:
            transitive.append(t[JavaInfo].plugins.processor_jars)
