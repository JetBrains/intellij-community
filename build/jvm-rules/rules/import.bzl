load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo")
load("//:rules/impl/compile.bzl", _KtWasmJsInfo = "KtWasmJsInfo")

visibility("private")

def _jvm_import(ctx):
    return [
        JavaInfo(
            output_jar = ctx.file.jar,
            compile_jar = ctx.file.jar,
            source_jar = ctx.file.source_jar,
            runtime_deps = [dep[JavaInfo] for dep in ctx.attr.runtime_deps],
        ),
        _KtJvmInfo(
            exported_compiler_plugins = depset(ctx.attr.exported_compiler_plugins),
        ),
    ]

jvm_import = rule(
    attrs = {
        "jar": attr.label(
            doc = """The jar listed here is equivalent to an export attribute.""",
            allow_single_file = True,
        ),
        "source_jar": attr.label(
            doc = """The sources for the class jar.""",
            allow_single_file = True,
        ),
        "runtime_deps": attr.label_list(
            doc = """Libraries to make available to the final binary or test at runtime only. Like ordinary deps, these will
        appear on the runtime classpath, but unlike them, not on the compile-time classpath.""",
            default = [],
            allow_files = False,
            providers = [JavaInfo],
        ),
        "exported_compiler_plugins": attr.label_list(
            doc = """Exported compiler plugins.""",
            default = [],
            providers = [[_KtCompilerPluginInfo]],
        ),
    },
    provides = [JavaInfo, _KtJvmInfo],
    implementation = _jvm_import,
)

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
