load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo")
load("//:rules/impl/compile-wasmjs.bzl", _KtWasmJsInfo = "KtWasmJsInfo")

visibility("private")

def _wasmjs_import(ctx):
    return [
        _KtWasmJsInfo(
            compile_klibs = depset([ctx.file.klib], transitive = []),
            link_klibs = depset([ctx.file.klib], transitive = []),
            klib = ctx.file.klib,
            source_jar = ctx.file.source_jar,
        ),
        _KtJvmInfo(
            exported_compiler_plugins = depset(ctx.attr.exported_compiler_plugins),
        ),
    ]

wasmjs_import = rule(
    attrs = {
        "klib": attr.label(
            doc = """The klib listed here is equivalent to an export attribute.""",
            allow_single_file = True,
        ),
        "source_jar": attr.label(
            doc = """The sources for the class jar.""",
            allow_single_file = True,
        ),
        "exported_compiler_plugins": attr.label_list(
            doc = """Exported compiler plugins.""",
            default = [],
            providers = [[_KtCompilerPluginInfo]],
        ),
    },
    provides = [_KtWasmJsInfo, _KtJvmInfo],
    implementation = _wasmjs_import,
)
