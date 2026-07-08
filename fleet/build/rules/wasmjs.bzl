load("@rules_jvm//:wasmjs.bzl", "wasmjs_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtCompilerPluginInfo = "KtCompilerPluginInfo")

# TODO: make it a symbolic macro if we manage to work around the usage of globs
def fleet_wasmjs_module(name, visibility, module_name, kotlinc_opts, deps = [], test_deps = [], exports = [], test_exported_deps = [], runtime_deps = [], plugins = []):
    common_main_sourceset_target_name = "%s_commonMain" % name
    native.filegroup(
        name = common_main_sourceset_target_name,
        srcs = native.glob([
            "genCommonMain/**/*.kt",
            "srcCommonMain/**/*.kt",
        ], allow_empty = True),
    )

    wasmjs_main_sourceset_target_name = "%s_wasmJsMain" % name
    native.filegroup(
        name = wasmjs_main_sourceset_target_name,
        srcs = native.glob(["srcWasmJsMain/**/*.kt"], allow_empty = True),
    )

    # TODO: wire `wasmjs_test` using `test_deps` once that rule is introduced in the monorepo

    wasmjs_library(
        name = "%s_lib" % name,
        module_name = module_name,
        fragment_refines = {"commonMain": [], "wasmJsMain": ["commonMain"]},
        visibility = visibility,
        fragment_sources = {"commonMain": common_main_sourceset_target_name, "wasmJsMain": wasmjs_main_sourceset_target_name},
        deps = deps,
        exports = exports,
        runtime_deps = runtime_deps,
        plugins = plugins,
        kotlinc_opts = kotlinc_opts,
    )
