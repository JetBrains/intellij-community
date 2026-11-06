"""Rules that unpack a ZIP or a TAR archive into a directory."""

_TAR_TOOLCHAIN_TYPE = "@tar.bzl//tar/toolchain:type"

_USAGE = """
    To put the entries into a plugin distribution, map the directory with a `pkg_files` target and give it to the
    `non_classpath_data` attribute of `ij_plugin_module`. Rename the directory to `""` to put its entries at the root.
"""

def _unpack_zip_archive_impl(ctx):
    archive = ctx.file.archive
    directory = ctx.actions.declare_directory(ctx.label.name)
    ctx.actions.run(
        executable = ctx.executable._zipper,
        arguments = ["x", archive.path, "-d", directory.path],
        inputs = [archive],
        outputs = [directory],
        mnemonic = "UnpackZipArchive",
        progress_message = "Unpacking %s for %%{label}" % archive.short_path,
    )
    return [DefaultInfo(files = depset([directory]))]

unpack_zip_archive = rule(
    doc = "Unpacks all entries of a ZIP archive into a directory with the name of the target.\n" + _USAGE,
    implementation = _unpack_zip_archive_impl,
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

def _unpack_tar_archive_impl(ctx):
    archive = ctx.file.archive
    directory = ctx.actions.declare_directory(ctx.label.name)
    bsdtar = ctx.toolchains[_TAR_TOOLCHAIN_TYPE]
    ctx.actions.run(
        executable = bsdtar.tarinfo.binary,
        arguments = ["-x", "-f", archive.path, "-C", directory.path],
        inputs = [archive],
        tools = bsdtar.default.files,
        outputs = [directory],
        env = bsdtar.tarinfo.default_env,
        mnemonic = "UnpackTarArchive",
        progress_message = "Unpacking %s for %%{label}" % archive.short_path,
    )
    return [DefaultInfo(files = depset([directory]))]

unpack_tar_archive = rule(
    doc = """Unpacks all entries of a TAR archive into a directory with the name of the target.

    The `bsdtar` toolchain of `tar.bzl` unpacks the archive and detects its compression.
""" + _USAGE,
    implementation = _unpack_tar_archive_impl,
    attrs = {
        "archive": attr.label(
            mandatory = True,
            allow_single_file = [".tar", ".tar.gz", ".tgz"],
            doc = "The archive to unpack, for example the `files` target of a `download_repo` repository.",
        ),
    },
    toolchains = [_TAR_TOOLCHAIN_TYPE],
)
