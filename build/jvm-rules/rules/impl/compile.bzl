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
    "JAVA_TOOLCHAIN_TYPE",
    KotlinInfo = "KtJvmInfo",
    _KtCompilerPluginInfo = "KtCompilerPluginInfo",
    _KtPluginConfiguration = "KtPluginConfiguration",
)
load("//:rules/common-attrs.bzl", "add_dicts")
load("//:rules/impl/associates.bzl", "get_associates")
load("//:rules/impl/builder-args.bzl", "init_builder_args")
load("//:rules/impl/javac-options.bzl", "JavacOptions")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions", "kotlinc_options_to_flags")
load("//:rules/resource.bzl", "ResourceGroupInfo")

visibility("private")

def find_java_toolchain(ctx, target):
    return ctx.toolchains[JAVA_TOOLCHAIN_TYPE].java if JAVA_TOOLCHAIN_TYPE in ctx.toolchains else target[java_common.JavaToolchainInfo]

def _java_info(target):
    return target[JavaInfo] if JavaInfo in target else None

def _partitioned_srcs(srcs):
    kt_srcs = []
    java_srcs = []
    form_srcs = []

    for f in srcs:
        if f.path.endswith(".kt"):
            kt_srcs.append(f)
        elif f.path.endswith(".java"):
            java_srcs.append(f)
        elif f.path.endswith(".form"):
            form_srcs.append(f)

    return struct(
        kt = kt_srcs,
        java = java_srcs,
        forms = form_srcs,
        all_srcs = kt_srcs + java_srcs + form_srcs,
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
    prune_transitive_deps = True and "kt_experimental_prune_transitive_deps_incompatible" not in ctx.attr.tags
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
        if KotlinInfo in dep and dep[KotlinInfo] != None:
            plugins.extend(dep[KotlinInfo].exported_compiler_plugins.to_list())
    return plugins

def _collect_plugins_for_export(local, exports):
    """Collects into a depset. """
    return depset(
        local,
        transitive = [
            e[KotlinInfo].exported_compiler_plugins
            for e in exports
            if KotlinInfo in e and e[KotlinInfo]
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

        existing = all_plugins.get(plugin.id)
        if existing:
            if existing != plugin:
                fail("has multiple plugins with the same id: %s." % plugin.id)
        else:
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

def kt_jvm_produce_jar_actions(ctx, isTest = False):
    """Sets up a compile action for a jar.

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
    plugins = _new_plugins_from(perTargetPlugins + _exported_plugins(ctx.attr.deps))

    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")

    transitiveInputs = [compile_deps.compile_jars]
    _collect_runtime_jars(perTargetPlugins, transitiveInputs)
    _collect_runtime_jars(ctx.attr.deps, transitiveInputs)

    compile_jar = _run_jvm_builder(
        ctx = ctx,
        output_jar = output_jar,
        srcs = srcs,
        resources = [r[ResourceGroupInfo] for r in ctx.attr.resources],
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
        kt = KotlinInfo(
            srcs = ctx.files.srcs,
            module_name = associates.module_name,
            module_jars = associates.jars,
            exported_compiler_plugins = _collect_plugins_for_export(ctx.attr.exported_compiler_plugins, ctx.attr.exports) if not isTest else depset(),
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
        resources,
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

    args = init_builder_args(ctx, srcs, resources, associates, transitiveInputs, plugins = plugins, compile_deps = compile_deps)
    args.add("--out", output_jar)

    outputs = [output_jar]
    abi_jar = output_jar
    abi_jar = ctx.actions.declare_file(ctx.label.name + ".abi.jar")
    outputs.append(abi_jar)
    args.add("--abi-out", abi_jar)

    javac_opts = ctx.attr.javac_opts[JavacOptions] if ctx.attr.javac_opts else None
    if javac_opts and javac_opts.add_exports:
        args.add_all("--add-export", javac_opts.add_exports)
    if javac_opts and javac_opts.no_proc:
        args.add("--no-proc")

    isIncremental = (kotlin_inc_threshold != -1 and len(srcs.kt) >= kotlin_inc_threshold) or (java_inc_threshold != -1 and len(srcs.java) >= java_inc_threshold)
    if not isIncremental:
        args.add("--non-incremental")

    javaCount = len(srcs.java)
    args.add("--java-count", javaCount)

    all_resources = [f for r in resources for f in r.files]

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]

    ctx.actions.run(
        mnemonic = "JvmCompile",
        env = {
            "MALLOC_ARENA_MAX": "2",
        },
        inputs = depset(srcs.all_srcs + all_resources, transitive = transitiveInputs),
        outputs = outputs,
        tools = [ctx.file._jvm_builder_launcher, ctx.file._jvm_builder],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._jvm_builder_jvm_flags[BuildSettingInfo].value + [
            ctx.file._jvm_builder_launcher.path,
            ctx.file._jvm_builder.path,
            args,
        ],
        progress_message = "compile %%{label} (kt: %d, java: %d%s}" % (len(srcs.kt), javaCount, "" if isIncremental else ", non-incremental"),
    )

    return abi_jar

def _collect_runtime_jars(targets, transitive):
    for t in targets:
        if JavaInfo in t:
            transitive.append(t[JavaInfo].plugins.processor_jars)

KtWasmJsInfo = provider(
    doc = "Information required to compile Kotlin to Wasm/JS",
    fields = {
        "compile_klibs": "depset(File): klibs visible on the compiler classpath, added to the compile library path of *direct* dependents",
        "link_klibs": "depset(File): klibs that must be given to the Wasm/JS linker, propagated transitively",
        "klib": "File: klib of this module",
        "source_jar": "File: sources of this module",
    },
)

KtWasmJsBin = provider(
    fields = {
        "mjs": "The linked `.mjs` module of that module",
    },
)

def wasmjs_kotlinc_options(kotlinc_options):
    jvm_specific_options = ["x_jvm_default", "jvm_target"]
    filtered_options = {
        k: getattr(kotlinc_options, k)
        for k in dir(kotlinc_options)
        if k not in jvm_specific_options  # exclude JVM specific options
    }
    return KotlincOptions(**filtered_options)

def create_wasmjs_compilation_common_args(ctx):
    kotlinc_options = wasmjs_kotlinc_options(ctx.attr.kotlinc_opts[KotlincOptions])

    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("-Xwasm")
    args.add("-Xwasm-target=js")
    args.add("-Xmulti-platform")
    args.add_all(kotlinc_options_to_flags(kotlinc_options))

    args.add("-ir-output-name", "%s_%s" % (ctx.attr.module_name, ctx.label.name))

    # TODO: add support for `-Xfriend-modules`

    return args

def kt_wasmjs_produce_module_actions(ctx, rule_kind):
    srcs = [f.path for f in ctx.files.fragment_sources]

    compile_exported_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].compile_klibs for d in ctx.attr.exports])
    compile_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].compile_klibs for d in ctx.attr.deps])

    link_exported_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].link_klibs for d in ctx.attr.exports])
    link_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].link_klibs for d in ctx.attr.deps])
    link_libraries = depset([], transitive = [link_exported_deps_klibs, link_deps_klibs])

    exported_deps_exported_compiler_plugins = depset([], transitive = [dep[KotlinInfo].exported_compiler_plugins for dep in ctx.attr.exports if dep[KotlinInfo]])

    if not srcs:
        return [
            KtWasmJsInfo(
                compile_klibs = depset([], transitive = [compile_exported_deps_klibs]),
                link_klibs = depset([], transitive = [link_libraries]),
                klib = None,
                source_jar = None,  # TODO: support that
            ),
            KtWasmJsBin(
                mjs = None,
            ),
            KotlinInfo(
                exported_compiler_plugins = exported_deps_exported_compiler_plugins,
            ),
            OutputGroupInfo(
                klib = [],
                js = [],
            ),
        ]

    compile_libraries = depset([], transitive = [compile_exported_deps_klibs, compile_deps_klibs])

    compile_args = create_wasmjs_compilation_common_args(ctx)
    klib_out = ctx.actions.declare_file("%s_%s.klib" % (ctx.attr.module_name, ctx.label.name))
    compile_args.add("-ir-output-dir", klib_out.dirname)
    compile_args.add("-Xir-produce-klib-file")
    all_fragments = set()
    for fragment_name, refined_fragments in ctx.attr.fragment_refines.items():
        all_fragments.add(fragment_name)
        for refined in refined_fragments:
            compile_args.add("-Xfragment-refines=%s:%s" % (fragment_name, refined))
    for fragment_name, fragment_files in ctx.attr.fragment_sources.items():
        all_fragments.add(fragment_name)
        for f in fragment_files.files.to_list():
            compile_args.add("-Xfragment-sources=%s:%s" % (fragment_name, f.path))
    compile_args.add_joined("-libraries", [klib.path for klib in compile_libraries.to_list()], join_with = ctx.configuration.host_path_separator, omit_if_empty = True)
    for fragment_name in all_fragments:
        compile_args.add("-Xfragments=%s" % fragment_name)

    perTargetPlugins = ctx.attr.plugins if hasattr(ctx.attr, "plugins") else []
    plugins = _new_plugins_from(perTargetPlugins + _exported_plugins(deps = ctx.attr.deps + ctx.attr.exports))

    # TODO: switch to -Xcompiler-plugin option when it's ready (currently a prototype, and K2-only) https://jetbrains.slack.com/archives/C942U8L4R/p1708709995859629
    # Note: this is technically wrong, because we resolve each compiler plugin classpath independently, but the
    # kotlin compiler loads all plugin classpaths together in a single classloader (so we don't have cross-plugin
    # conflict resolution). At the moment, all compiler plugins have a single jar, so this is not a problem.
    # The proper way would be to resolve all plugins in a single resolution scope to resolve conflicts.
    # However, the new -Xcompiler-plugin argument will actually work with one classpath per plugin, loaded in
    # independent classloaders, so we would have to change it back when doing the switch, so let's wait instead.
    compiler_plugins_classpath = depset([], transitive = [classpath for _, classpath in plugins.compile_phase.classpath.items()])
    compile_args.add_all(["-Xplugin=%s" % jar.path for jar in compiler_plugins_classpath.to_list()])
    for id, options in plugins.compile_phase.options.items():
        compile_args.add_all(["-Pplugin:%s:%s=%s" % id % opt.id % opt.value for opt in options])
    compile_args.add_all(srcs)

    ctx.actions.run(
        mnemonic = "KotlinCompileWasmJs",
        inputs = depset(ctx.files.fragment_sources, transitive = [compile_libraries, compiler_plugins_classpath]),
        use_default_shell_env = True,
        outputs = [klib_out],
        executable = ctx.executable._wasmjs_builder,
        arguments = [compile_args],
        progress_message = "Compile %%{label} { files: %d }" % (len(ctx.files.fragment_sources)),
        env = {
            "LC_CTYPE": "en_US.UTF-8",
        },
    )

    all_link_libraries = depset([klib_out], transitive = [link_libraries])

    mjs_out = ctx.actions.declare_directory("%s_%s-js" % (ctx.attr.module_name, ctx.label.name))
    link_args = create_wasmjs_compilation_common_args(ctx)
    link_args.add("-ir-output-dir", mjs_out.path)
    link_args.add("-Xir-produce-js")
    link_args.add("-Xinclude=%s" % klib_out.path)  # TODO: what is the `-Xinclude`, is that what will be linked? Do we need `runtime_deps_klibs` here?
    link_args.add("-Xir-dce")
    link_args.add_joined("-libraries", [klib.path for klib in all_link_libraries.to_list()], join_with = ":", omit_if_empty = True)

    ctx.actions.run(
        mnemonic = "KotlinLinkWasmJs",
        inputs = all_link_libraries,
        use_default_shell_env = True,
        outputs = [mjs_out],
        executable = ctx.executable._wasmjs_builder,
        arguments = [link_args],
        progress_message = "Linking %%{label}",
        env = {
            "LC_CTYPE": "en_US.UTF-8",
        },
    )

    return [
        KtWasmJsInfo(
            compile_klibs = depset([klib_out], transitive = [compile_exported_deps_klibs]),
            link_klibs = all_link_libraries,
            klib = klib_out,
            source_jar = None,  # TODO: support that
        ),
        KtWasmJsBin(
            mjs = mjs_out,
        ),
        KotlinInfo(
            exported_compiler_plugins = exported_deps_exported_compiler_plugins,
        ),
        DefaultInfo(
            files = depset([klib_out, mjs_out], transitive = []),  # run both compilation and linking when running `bazel build`
        ),
        OutputGroupInfo(
            klib = depset([klib_out], transitive = []),
            mjs = depset([mjs_out], transitive = []),
        ),
    ]
