"""A dev-dist tool binary that every product shares."""

load("//build/rust-tools:defs.bzl", "RUST_TOOLCHAIN_TYPE", "RUST_TOOL_IDENTITY_ATTRS", "rust_tool_identity_inputs")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_neutral_product_transition")
load(":dev_dist_tool_info.bzl", "DevDistToolInfo")

def _dev_dist_tool_impl(ctx):
    tool = ctx.executable.tool

    # Under a directory of the target's name, so the executable keeps the tool's own basename.
    executable = ctx.actions.declare_file(ctx.label.name + "/" + tool.basename)
    ctx.actions.symlink(output = executable, target_file = tool, is_executable = True)

    # The identity has one path on every host, so its name drops the `.exe` of a Windows binary.
    name = tool.basename.removesuffix(".exe")
    inputs = rust_tool_identity_inputs(ctx, ctx.attr.tool)
    files = inputs.files + ctx.files._manifests
    entries = ctx.actions.args()
    entries.set_param_file_format("multiline")
    entries.add_all(inputs.facts, format_each = "fact=%s")
    entries.add_all(files, map_each = _identity_file_entry)
    flag_file = ctx.actions.declare_file(ctx.label.name + "/" + name + ".identity-entries")
    ctx.actions.write(flag_file, entries)

    identity = ctx.actions.declare_file(ctx.label.name + "/" + name + ".identity")
    ctx.actions.run(
        executable = ctx.executable._identity_writer,
        arguments = ["--flagfile=" + flag_file.path, "--output=" + identity.path],
        inputs = files + [flag_file],
        outputs = [identity],
        mnemonic = "DevDistToolIdentity",
        progress_message = "Writing the identity of %s" % tool.basename,
    )
    return [
        DefaultInfo(executable = executable, files = depset([executable])),
        DevDistToolInfo(identity = identity),
    ]

def _identity_file_entry(file):
    return "file=%s=%s" % (file.short_path, file.path)

dev_dist_tool = rule(
    doc = """One tool binary for all products, with its identity.

A rule reached in a product configuration names its tool with `cfg = "exec"`. The exec configuration keeps the product
flag, so each product compiled its own copy of the tool, and a worker tool started one process per product. This rule
resets the flag, so every product gets the same binary.

`DevDistToolInfo` holds `<name>/<tool>.identity`, and every action of the tool declares it as an input with
`dev_dist_tool_inputs`. The file states the crates, the sources, the Rust toolchain and the rules_rs sources of the tool,
and it is equal on every host. The remote cache key of an action of the tool omits the binary and its runfiles and keeps
this file, see `community/build/bazel_scrubbing.cfg`.""",
    implementation = _dev_dist_tool_impl,
    cfg = dev_dist_neutral_product_transition,
    executable = True,
    toolchains = [RUST_TOOLCHAIN_TYPE],
    attrs = dict(
        RUST_TOOL_IDENTITY_ATTRS,
        tool = attr.label(mandatory = True, executable = True, cfg = "target", doc = "The tool binary."),
        _allowlist_function_transition = attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
        _identity_writer = attr.label(
            default = "//build/dev-dist-tools/bins/tool-identity",
            executable = True,
            cfg = "exec",
        ),
        _manifests = attr.label(default = "//build/dev-dist-tools:tool_identity_manifests"),
    ),
)
