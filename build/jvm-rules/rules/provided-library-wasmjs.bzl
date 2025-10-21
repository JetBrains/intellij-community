load("@rules_kotlin//kotlin/internal:defs.bzl", KotlinInfo = "KtJvmInfo")
load("//:rules/impl/compile-wasmjs.bzl", "KtWasmJsInfo")

visibility("private")

def _wasmjs_provided_libraries(ctx):
    lib = ctx.attr.exports[0]

    resultWasmjsInfo = KtWasmJsInfo(
        compile_klibs = lib[KtWasmJsInfo].compile_klibs,
        link_klibs = depset([]),  # never link provided libraries
        klib = lib[KtWasmJsInfo].klib,
        source_jar = lib[KtWasmJsInfo].source_jar,
    )

    kotlinInfo = lib[KotlinInfo] if KotlinInfo in lib else None
    if kotlinInfo and hasattr(kotlinInfo, "exported_compiler_plugins"):
        exported_compiler_plugins = kotlinInfo.exported_compiler_plugins
        if exported_compiler_plugins:
            return [resultWasmjsInfo, KotlinInfo(exported_compiler_plugins = exported_compiler_plugins)]

    return [resultWasmjsInfo, KotlinInfo(exported_compiler_plugins = depset())]

_wasmjs_rule = rule(
    attrs = {
        "exports": attr.label_list(
            doc = """exports attribute instead of a singular lib attribute. This way the Bazel plugin can recognize it""",
            mandatory = True,
            allow_files = False,
            providers = [[KtWasmJsInfo], [KtWasmJsInfo, KotlinInfo]],
        ),
    },
    provides = [KtWasmJsInfo, KotlinInfo],
    implementation = _wasmjs_provided_libraries,
)

def _wasmjs_provided_library(name, visibility, lib):
    return _wasmjs_rule(
        name = name,
        visibility = visibility,
        exports = [lib],
    )

wasmjs_provided_library = macro(
    attrs = {
        "lib": attr.label(mandatory = True, allow_files = False, providers = [KtWasmJsInfo, KotlinInfo], configurable = False),
    },
    implementation = _wasmjs_provided_library,
)
