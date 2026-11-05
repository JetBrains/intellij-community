"""A rule that unpacks a ZIP archive into a directory."""

def _unpack_archive_impl(ctx):
    archive = ctx.file.archive
    directory = ctx.actions.declare_directory(ctx.label.name)
    ctx.actions.run(
        executable = ctx.executable._zipper,
        arguments = ["x", archive.path, "-d", directory.path],
        inputs = [archive],
        outputs = [directory],
        mnemonic = "UnpackArchive",
        progress_message = "Unpacking %s for %%{label}" % archive.short_path,
    )
    return [DefaultInfo(files = depset([directory]))]

unpack_archive = rule(
    doc = """Unpacks all entries of a ZIP archive into a directory with the name of the target.

    To put the entries into a plugin distribution, map the directory with a `pkg_files` target and give it to the
    `non_classpath_data` attribute of `ij_plugin_module`. Rename the directory to `""` to put its entries at the root.
    """,
    implementation = _unpack_archive_impl,
    attrs = {
        "archive": attr.label(
            mandatory = True,
            allow_single_file = [".zip", ".jar"],
            doc = "The archive to unpack, for example the `files` target of a `download_repo` repository.",
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
            cfg = "exec",
        ),
    },
)
