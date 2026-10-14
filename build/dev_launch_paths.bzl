"""The absolute local paths that a dev launcher writes into its `java` argument file, as constants of `@dev_launch_paths`.

`java` expands no variable, and a launch runs no code before the JVM. So the paths that the launcher of ADR 0014 read
from the environment at launch are constants at analysis: the workspace root, the output base and the home directory of
the user. The repository reads no workspace file. `getenv` tracks `HOME`, and a moved checkout has another output base,
which fetches the repository again.

Only the argument file action, `RunEnvironmentInfo`, the `args` of a launcher and the sibling vmoptions file of an
export read the constants. Each of these is local, so no remotely cached action key holds a local path. The extension
is reproducible, so the lockfile holds no local path either.
"""

def _dev_launch_paths_impl(repository_ctx):
    # The repository directory is `<output base>/external/<canonical name>`.
    output_base = repository_ctx.path(".").dirname.dirname
    if not output_base.get_child("execroot").exists:
        fail("%s is not the output base: it has no execroot. A launcher cannot name its runfiles" % output_base)
    repository_ctx.file("BUILD.bazel", "exports_files([\"paths.bzl\"])\n")
    repository_ctx.file("paths.bzl", "\n".join([
        '"""The local paths of this checkout, written by `dev_launch_paths`. See `@community//build:dev_launch_paths.bzl`."""',
        "",
        "WORKSPACE_ROOT = %s" % repr(str(repository_ctx.workspace_root.realpath)),
        "OUTPUT_BASE = %s" % repr(str(output_base)),
        "HOME = %s" % repr(repository_ctx.getenv("HOME", "")),
        "",
    ]))

_dev_launch_paths = repository_rule(
    implementation = _dev_launch_paths_impl,
    local = True,
    doc = "The local paths of the checkout, see the file documentation.",
)

def _dev_launch_paths_extension_impl(module_ctx):
    _dev_launch_paths(name = "dev_launch_paths")
    return module_ctx.extension_metadata(reproducible = True)

dev_launch_paths = module_extension(implementation = _dev_launch_paths_extension_impl)
