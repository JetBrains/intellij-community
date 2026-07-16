load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("//:rules/common-attrs.bzl", "add_dicts", "common_toolchains")
load("//:rules/impl/compile-wasmjs.bzl", "KtWasmJsInfo", "wasmjs_link_action")
load("//:rules/impl/kotlinc-options.bzl", "KotlincExtraOptionsInfo", "KotlincOptions")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition", "scrubbed_host_platform_transition")
load("//:rules/opt-wasmjs.bzl", "WASM_OPT_IMPLICIT_ATTRS", "wasm_opt_action")

visibility("private")

KtWasmJsBinaryInfo = provider(
    doc = "Linked (and optionally binaryen-optimized) WasmJS application.",
    fields = {
        "linked_directory": "Directory: the KotlinLinkWasmJs output (`<ir_output_name>-js`).",
        "optimized_directory": "Directory: linked_directory with every .wasm optimized by wasm-opt, non-wasm files copied through.",
        "functions_map_directory": "Directory: per-wasm-file symbol maps (wasm-opt --symbolmap), named `<wasm basename without extension>.txt`.",
        "dist_directory": "Directory: what the `optimize` toggle selected (optimized_directory or linked_directory).",
    },
)

def _wasmjs_ir_output_name(ctx):
    return ctx.attr.ir_output_name or ctx.attr.module_name or ctx.label.name

def _wasmjs_binary(ctx):
    module_info = ctx.attr.module[KtWasmJsInfo]
    if module_info.klib == None:
        fail("%s does not produce a klib (no sources?); wasmjs_binary needs a klib to link via -Xinclude" % ctx.attr.module.label)

    ir_output_name = _wasmjs_ir_output_name(ctx)
    linked_dir = wasmjs_link_action(ctx, ir_output_name, module_info.klib, module_info.link_klibs)

    # Always registered; Bazel only executes it when the optimized outputs are requested, so
    # optimize = "off" builds never pay for wasm-opt while `--output_groups=optimized` stays available.
    opt = wasm_opt_action(ctx, linked_dir, "%s-js" % ir_output_name)

    opt_enabled = ctx.attr.optimize == "on" or (ctx.attr.optimize == "auto" and ctx.attr._wasm_opt_default[BuildSettingInfo].value)
    default_files = [opt.optimized_directory, opt.functions_map_directory] if opt_enabled else [linked_dir]

    return [
        DefaultInfo(files = depset(default_files)),
        KtWasmJsBinaryInfo(
            linked_directory = linked_dir,
            optimized_directory = opt.optimized_directory,
            functions_map_directory = opt.functions_map_directory,
            dist_directory = opt.optimized_directory if opt_enabled else linked_dir,
        ),
    ]

wasmjs_binary = rule(
    doc = """Links a compiled wasmjs_library module (and its transitive klibs) into a WasmJS application
      directory, optionally optimized with binaryen's wasm-opt (see the `optimize` attribute).""",
    attrs = WASM_OPT_IMPLICIT_ATTRS | {
        "module": attr.label(
            mandatory = True,
            providers = [[KtWasmJsInfo]],
            doc = """The wasmjs_library whose klib is the linking entry point (`-Xinclude`); its
              transitive `link_klibs` provide the `-libraries` of the link.""",
        ),
        "module_name": attr.string(
            doc = "The name of the linked module; used for output file naming.",
        ),
        "ir_output_name": attr.string(
            doc = """Value for the compiler's `-ir-output-name`: determines the per-module JS/Wasm file
              names emitted by (multimodule) linking. Defaults to `module_name`.""",
        ),
        "kotlinc_opts": attr.label(
            doc = """Link-time kotlinc options (e.g. `x_wasm_attach_js_exception`,
              `x_wasm_generate_closed_world_multimodule`); should match the options the module was compiled with.""",
            default = "//:default-kotlinc-opts",
            providers = [[KotlincOptions, KotlincExtraOptionsInfo]],
        ),
        "optimize": attr.string(
            default = "auto",
            values = ["auto", "on", "off"],
            doc = """Whether the default output is the wasm-opt-optimized WasmJS directory ("on") or the
              plain linked one ("off"). "auto" follows the `--@rules_jvm//:wasm-opt-default` flag. Either
              flavor stays requestable through the `linked`/`optimized`/`functions_map` output groups.""",
        ),
        "_wasm_opt_default": attr.label(
            default = "//:wasm-opt-default",
        ),
        "_wasm_source_maps": attr.label(
            default = "//:wasm-source-maps",
        ),
        "_wasmjs_builder": attr.label(
            default = "//kotlin-builder-wasmjs:kotlin-builder-wasmjs_deploy.jar",
            allow_single_file = True,
            cfg = scrubbed_host_platform_transition,
        ),
        "_wasmjs_builder_jvm_flags": attr.label(
            default = "//kotlin-builder-wasmjs:kotlin-builder-wasmjs-jvm_flags",
        ),
        "_wasmjs_builder_launcher": attr.label(
            default = "//:rules/impl/MemoryLauncher.java",
            allow_single_file = True,
        ),
        "_tool_java_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
        ),
    },
    toolchains = common_toolchains,
    implementation = _wasmjs_binary,
    provides = [KtWasmJsBinaryInfo],
    cfg = jvm_platform_transition,
)
