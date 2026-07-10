load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo")
load("//:rules/impl/compile-wasmjs.bzl", _KtWasmJsInfo = "KtWasmJsInfo")

visibility("private")

def _wasmjs_import(ctx):
    return [
        _KtWasmJsInfo(
            compile_klibs = depset([ctx.file.klib], transitive = [dep[_KtWasmJsInfo].compile_klibs for dep in ctx.attr.exported_deps]),  # Compile visibility follows exported deps only
            link_klibs = depset([ctx.file.klib], transitive = [dep[_KtWasmJsInfo].link_klibs for dep in ctx.attr.deps + ctx.attr.exported_deps]),  # Link visibility follows the full runtime closure
            klib = ctx.file.klib,
            source_jar = ctx.file.source_jar,
        ),
        _KtJvmInfo(
            exported_compiler_plugins = depset(ctx.attr.exported_compiler_plugins),
        ),
    ]

wasmjs_import = rule(
    implementation = _wasmjs_import,
    attrs = {
        "klib": attr.label(
            allow_single_file = True,
            doc = ".klib of this imported dependency, exposed to the compile library path of direct dependents.",
        ),
        "source_jar": attr.label(
            allow_single_file = True,
            doc = "Source jars exposed by this imported dependency.",
        ),
        "exported_compiler_plugins": attr.label_list(
            doc = """Exported compiler plugins.""",
            default = [],
            providers = [[_KtCompilerPluginInfo]],
        ),
        "deps": attr.label_list(
            providers = [[_KtWasmJsInfo]],
            doc = "Dependencies of this imported dependency, exposed to the link path of dependents transitively.",
        ),
        "exported_deps": attr.label_list(
            providers = [[_KtWasmJsInfo]],
            doc = "Dependencies of this imported dependency, exposed to the link path of dependents transitively, and exposed to the compile library path of *direct* dependents.",
        ),
    },
    provides = [_KtWasmJsInfo, _KtJvmInfo],
)
