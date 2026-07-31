load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java/common:java_common.bzl", "java_common")
load("//:rules/impl/transitions.bzl", "scrubbed_host_platform_transition")

visibility("private")

# Default wasm-opt arguments of the Kotlin Gradle plugin: BinaryenConfig.binaryenCommonArgs +
# binaryenMultimoduleArgs (the list KGP selects when linking produced more than one wasm module,
# which is the closed-world multimodule case). Extracted from kotlin-gradle-plugin 2.4.10-RC2;
# keep in sync with the Kotlin version used by the wasmjs rules (`_KOTLIN_VERSION` in MODULE.bazel).
_KGP_BINARYEN_MULTIMODULE_ARGS = [
    "--enable-gc",
    "--enable-reference-types",
    "--enable-exception-handling",
    "--enable-bulk-memory",
    "--enable-nontrapping-float-to-int",
    "--no-inline=kotlin.wasm.internal.throwValue",
    "--no-inline=kotlin.wasm.internal.getKotlinException",
    "--no-inline=kotlin.wasm.internal.jsToKotlinStringAdapter",
    "--inline-functions-with-loops",
    "--traps-never-happen",
    "--fast-math",
    "-O3",
    "-O3",
    "-O3",
]

# Implicit attributes required on any rule that calls `wasm_opt_action` (in addition to
# `_tool_java_runtime` from common-attrs.bzl `_implicit_deps`).
WASM_OPT_IMPLICIT_ATTRS = {
    "_wasm_opt": attr.label(
        default = "//:wasm-opt",
        cfg = "exec",
    ),
    "_wasm_opt_worker": attr.label(
        default = "//wasm-opt-worker:wasm-opt-worker_deploy.jar",
        allow_single_file = True,
        cfg = scrubbed_host_platform_transition,
    ),
    "_wasm_opt_worker_jvm_flags": attr.label(
        default = "//wasm-opt-worker:wasm-opt-worker-jvm_flags",
    ),
    "_wasm_opt_worker_launcher": attr.label(
        default = "//:rules/impl/MemoryLauncher.java",
        allow_single_file = True,
    ),
}

def wasm_opt_action(ctx, linked_dir, output_prefix):
    """Registers the WasmOptWasmJs action running binaryen's wasm-opt over every .wasm file of `linked_dir`.

    Requires on ctx: the WASM_OPT_IMPLICIT_ATTRS attributes plus `_tool_java_runtime`.

    Returns struct(
        optimized_directory: `<output_prefix>-optimized`, the linked directory with every .wasm
            optimized, non-wasm files copied through,
        functions_map_directory: `<output_prefix>-functions-map`, per-wasm-file symbol maps
            (wasm-opt --symbolmap), named `<wasm basename without extension>.txt`,
    ).
    """
    wasm_opt_files = ctx.files._wasm_opt
    wasm_opt_executables = [f for f in wasm_opt_files if f.basename in ["wasm-opt", "wasm-opt.exe"]]
    if len(wasm_opt_executables) != 1:
        fail("Expected exactly one wasm-opt executable among %s" % wasm_opt_files)

    optimized_dir = ctx.actions.declare_directory("%s-optimized" % output_prefix)
    functions_map_dir = ctx.actions.declare_directory("%s-functions-map" % output_prefix)

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]

    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--input-directory=%s" % linked_dir.path)
    args.add("--output-directory=%s" % optimized_dir.path)
    args.add("--functions-map-directory=%s" % functions_map_dir.path)
    args.add("--wasm-opt=%s" % wasm_opt_executables[0].path)
    args.add_all(_KGP_BINARYEN_MULTIMODULE_ARGS, format_each = "--wasm-opt-arg=%s")

    ctx.actions.run(
        mnemonic = "WasmOptWasmJs",
        inputs = depset([linked_dir], transitive = [java_runtime.files]),
        outputs = [optimized_dir, functions_map_dir],
        tools = [ctx.file._wasm_opt_worker_launcher, ctx.file._wasm_opt_worker] + wasm_opt_files,
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._wasm_opt_worker_jvm_flags[BuildSettingInfo].value + [
            ctx.file._wasm_opt_worker_launcher.path,
            ctx.file._wasm_opt_worker.path,
            args,
        ],
        progress_message = "Optimizing WasmJS modules %{label}",
    )

    return struct(
        optimized_directory = optimized_dir,
        functions_map_directory = functions_map_dir,
    )
