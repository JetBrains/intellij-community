"""The gzip resources of one module: each `.xml` entry of the library jars as one gzip member file."""

load("@rules_jvm//:jvm.bzl", "ResourceGroupInfo")

def _gzip_resources_impl(ctx):
    directory = ctx.actions.declare_directory(ctx.label.name)
    args = ctx.actions.args()
    args.add("gzip-resources")
    args.add("--output-dir=" + directory.path)
    args.add_all(ctx.files.srcs)
    ctx.actions.run(
        executable = ctx.executable._packer,
        arguments = [args],
        inputs = ctx.files.srcs,
        outputs = [directory],
        mnemonic = "GzipResources",
        progress_message = "Writing the gzip resources of %{label}",
    )
    return [
        ResourceGroupInfo(files = [directory], strip_prefix = directory.path, add_prefix = ""),
        DefaultInfo(files = depset([directory])),
    ]

gzip_resources = rule(
    implementation = _gzip_resources_impl,
    doc = """Writes `<entry>.gzip` for each `.xml` entry of the `srcs` jars, for the `resource_jars` of a module.

The gzip member keeps the deflate stream of the jar entry, so no deflater runs. The plugin jar stores every entry
uncompressed, and the runtime reads the member through `GZIPInputStream`. The BUILD file generator declares the
target for the modules in `GZIP_RESOURCE_MODULES` over their provided module libraries.
""",
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_files = [".jar"],
            doc = "The library jars in the order of the module libraries. The first jar that holds a name wins.",
        ),
        "_packer": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_remainder_packer",
            executable = True,
            cfg = "exec",
        ),
    },
)
