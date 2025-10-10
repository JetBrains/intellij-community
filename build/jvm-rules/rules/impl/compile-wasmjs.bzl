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
load("@rules_kotlin//kotlin/internal:defs.bzl", KotlinInfo = "KtJvmInfo")
load("//:rules/impl/compiler-plugins.bzl", "compiler_plugins_from", "exported_compiler_plugins_from")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions", "kotlinc_options_to_flags")

visibility("private")

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

def _wasmjs_kotlinc_options(kotlinc_options):
    jvm_specific_options = ["x_jvm_default", "jvm_target"]
    filtered_options = {
        k: getattr(kotlinc_options, k)
        for k in dir(kotlinc_options)
        if k not in jvm_specific_options  # exclude JVM specific options
    }
    return KotlincOptions(**filtered_options)

def _create_wasmjs_compilation_common_args(ctx):
    kotlinc_options = _wasmjs_kotlinc_options(ctx.attr.kotlinc_opts[KotlincOptions])

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

def wasmjs_produce_module_actions(ctx, rule_kind):
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

    compile_args = _create_wasmjs_compilation_common_args(ctx)
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
    plugins = compiler_plugins_from(perTargetPlugins + exported_compiler_plugins_from(deps = ctx.attr.deps + ctx.attr.exports))

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

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]

    ctx.actions.run(
        mnemonic = "KotlinCompileWasmJs",
        inputs = depset(ctx.files.fragment_sources, transitive = [compile_libraries, compiler_plugins_classpath, java_runtime.files]),  # TODO: remove `java_runtime.files` when running worker support is done (redundant then)
        outputs = [klib_out],
        tools = [ctx.file._wasmjs_builder_launcher, ctx.file._wasmjs_builder],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "0",  # TODO: [FL-34215] enable worker support
            "supports-multiplex-workers": "0",  # TODO: [FL-34215] enable worker support
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._wasmjs_builder_jvm_flags[BuildSettingInfo].value + [
            ctx.file._wasmjs_builder_launcher.path,
            ctx.file._wasmjs_builder.path,
            compile_args,
        ],
        progress_message = "Compile %%{label} { files: %d }" % (len(ctx.files.fragment_sources)),
    )

    all_link_libraries = depset([klib_out], transitive = [link_libraries])

    mjs_out = ctx.actions.declare_directory("%s_%s-js" % (ctx.attr.module_name, ctx.label.name))
    link_args = _create_wasmjs_compilation_common_args(ctx)
    link_args.add("-ir-output-dir", mjs_out.path)
    link_args.add("-Xir-produce-js")
    link_args.add("-Xinclude=%s" % klib_out.path)  # TODO: what is the `-Xinclude`, is that what will be linked? Do we need `runtime_deps_klibs` here?
    link_args.add("-Xir-dce")
    link_args.add_joined("-libraries", [klib.path for klib in all_link_libraries.to_list()], join_with = ":", omit_if_empty = True)

    ctx.actions.run(
        mnemonic = "KotlinLinkWasmJs",
        inputs = depset(transitive = [all_link_libraries, java_runtime.files]),  # TODO: remove `java_runtime.files` when running worker support is done (redundant then)
        outputs = [mjs_out],
        tools = [ctx.file._wasmjs_builder_launcher, ctx.file._wasmjs_builder],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "0",  # TODO: [FL-34215] enable worker support
            "supports-multiplex-workers": "0",  # TODO: [FL-34215] enable worker support
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._wasmjs_builder_jvm_flags[BuildSettingInfo].value + [
            ctx.file._wasmjs_builder_launcher.path,
            ctx.file._wasmjs_builder.path,
            link_args,
        ],
        progress_message = "Linking %%{label}",
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
