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
load("@rules_java//java:defs.bzl", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", KotlinInfo = "KtJvmInfo")
load("//:rules/impl/compiler-plugins.bzl", "compiler_plugins_from", "exported_compiler_plugins_from")
load("//:rules/impl/kotlinc-options.bzl", "KotlincExtraOptionsInfo", "KotlincOptions", "kotlinc_options_to_flags")

visibility("private")

KtWasmJsInfo = provider(
    doc = "Information required to compile Kotlin to Wasm/JS",
    fields = {
        "compile_klibs": "depset(File): klibs visible on the compiler classpath of *direct* dependents",
        "link_klibs": "depset(File): klibs that must be given to the Wasm/JS linker, propagated transitively",
        "klib": "File: klib of this module",
        "source_jar": "File: sources of this module",
    },
)

def _wasmjs_ir_output_name(ctx):
    return ctx.attr.ir_output_name or ctx.attr.module_name

def _wasmjs_kotlinc_options(kotlinc_options):
    jvm_specific_options = ["jvm_default", "jvm_target"]
    filtered_options = {
        k: getattr(kotlinc_options, k)
        for k in dir(kotlinc_options)
        if k not in jvm_specific_options  # exclude JVM specific options
    }
    return KotlincOptions(**filtered_options)

def _create_wasmjs_common_args(ctx, kotlinc_opts_target, ir_output_name):
    kotlinc_options = _wasmjs_kotlinc_options(kotlinc_opts_target[KotlincOptions])
    kotlinc_extra_options = kotlinc_opts_target[KotlincExtraOptionsInfo]

    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("-Xwasm")
    args.add("-Xwasm-target=js")
    args.add("-Xmulti-platform")
    args.add_all(kotlinc_options_to_flags(kotlinc_options, kotlinc_extra_options))

    args.add("-ir-output-name", ir_output_name)

    # Emitted per-module file name in multimodule linking; without it the linker derives an escaped
    # name from the klib uniqueName (e.g. `_fleet.build-x_`). Same flag the Kotlin Gradle plugin sets
    # per compilation (KotlinJsIrSubTarget).
    args.add("-Xir-per-module-output-name=%s" % ir_output_name)

    # TODO: add support for `-Xfriend-modules`

    return args

def wasmjs_compile_actions(ctx):
    srcs = [f.path for f in ctx.files.fragment_sources]

    compile_exported_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].compile_klibs for d in ctx.attr.exports])
    compile_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].compile_klibs for d in ctx.attr.deps])
    compile_libraries = depset([], transitive = [compile_exported_deps_klibs, compile_deps_klibs])

    link_exported_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].link_klibs for d in ctx.attr.exports])
    link_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].link_klibs for d in ctx.attr.deps])
    link_runtime_deps_klibs = depset([], transitive = [d[KtWasmJsInfo].link_klibs for d in ctx.attr.runtime_deps])
    link_libraries = depset([], transitive = [link_exported_deps_klibs, link_deps_klibs, link_runtime_deps_klibs])

    exported_deps_exported_compiler_plugins = depset([], transitive = [dep[KotlinInfo].exported_compiler_plugins for dep in ctx.attr.exports if KotlinInfo in dep])

    if not srcs:
        return [
            KtWasmJsInfo(
                compile_klibs = compile_exported_deps_klibs,
                link_klibs = link_libraries,
                klib = None,
                source_jar = None,  # TODO: support that
            ),
            KotlinInfo(
                exported_compiler_plugins = exported_deps_exported_compiler_plugins,
            ),
        ]

    compile_args = _create_wasmjs_common_args(ctx, ctx.attr.kotlinc_opts, _wasmjs_ir_output_name(ctx))
    klib_out = ctx.actions.declare_file("%s.klib" % _wasmjs_ir_output_name(ctx))
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

    plugins = compiler_plugins_from(ctx.attr.plugins + exported_compiler_plugins_from(deps = ctx.attr.deps + ctx.attr.exports))

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
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
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

    return [
        KtWasmJsInfo(
            compile_klibs = depset([klib_out], transitive = [compile_exported_deps_klibs]),
            link_klibs = all_link_libraries,
            klib = klib_out,
            source_jar = None,  # TODO: support that
        ),
        KotlinInfo(
            exported_compiler_plugins = exported_deps_exported_compiler_plugins,
        ),
        DefaultInfo(
            files = depset([klib_out]),
        ),
    ]

def wasmjs_link_action(ctx, ir_output_name, module_klib, link_klibs):
    """Registers the KotlinLinkWasmJs action linking `module_klib` against `link_klibs`.

    `link_klibs` is expected to already contain `module_klib` (as `KtWasmJsInfo.link_klibs` does).
    Requires on ctx: kotlinc_opts, _wasm_source_maps, _wasmjs_builder, _wasmjs_builder_jvm_flags,
    _wasmjs_builder_launcher, _tool_java_runtime.

    Returns the declared linked output directory (`<ir_output_name>-js`).
    """
    mjs_out = ctx.actions.declare_directory("%s-js" % ir_output_name)
    link_args = _create_wasmjs_common_args(ctx, ctx.attr.kotlinc_opts, ir_output_name)
    link_args.add("-ir-output-dir", mjs_out.path)
    link_args.add("-Xir-produce-js")
    link_args.add("-Xinclude=%s" % module_klib.path)  # TODO: what is the `-Xinclude`, is that what will be linked?
    link_args.add("-Xir-dce")
    if ctx.attr._wasm_source_maps[BuildSettingInfo].value:
        link_args.add("-source-map")
    link_args.add_joined("-libraries", [klib.path for klib in link_klibs.to_list()], join_with = ctx.configuration.host_path_separator, omit_if_empty = True)

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]

    ctx.actions.run(
        mnemonic = "KotlinLinkWasmJs",
        inputs = depset(transitive = [link_klibs, java_runtime.files]),  # TODO: remove `java_runtime.files` when running worker support is done (redundant then)
        outputs = [mjs_out],
        tools = [ctx.file._wasmjs_builder_launcher, ctx.file._wasmjs_builder],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._wasmjs_builder_jvm_flags[BuildSettingInfo].value + [
            ctx.file._wasmjs_builder_launcher.path,
            ctx.file._wasmjs_builder.path,
            link_args,
        ],
        progress_message = "Linking %{label}",
    )
    return mjs_out
