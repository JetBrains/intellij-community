"""
Generic helpers to declare per-module test dependencies repositories.

Why this file exists: Starlark module files loaded via use_repo_rule are frozen
at analysis-time, so mutating globals (like appending to a list) can fail.
To avoid that, this helper exposes a small factory that returns per-module
stateful functions, so each module has its own isolated, non-frozen state.

Usage pattern (per module):
  //path/to/module:module_test_dependencies.bzl
    load("@community//build:test_deps_extension.bzl", "test_deps_repository")
    _t = test_deps_repository("my_repo_name")
    download_file = _t.download_file
    download_file(name = "foo.zip", url = "...", sha256 = "...")
    module_test_deps_repository = _t.make_repository_rule()
    all_targets = _t.all_targets()

  In MODULE.bazel:
    module_test_deps = use_repo_rule("//path/to/module:module_test_dependencies.bzl", "test_deps_repository")
    module_test_deps(name = "my_repo_name")

Then BUILD files can use labels like @my_repo_name//:foo.zip or the convenience list
  load("//path/to/module:module_test_dependencies.bzl", "all_targets")
  ... deps = all_targets + [...]
"""

# NOTE: We intentionally do NOT implement a module_extension with tag classes here
# because the desired workflow is to declare files in per-module .bzl files, not
# inside MODULE.bazel. The use_repo_rule pattern fits that requirement.

load("@bazel_tools//tools/build_defs/repo:utils.bzl", "get_auth")

_PRELOADED_DOWNLOADS_MANIFEST = "preloaded-downloads-v1.tsv"
_PRELOADED_DOWNLOADS_MANIFEST_HEADER = "intellij-build-downloads\t1"

def write_downloads_repo(repository_ctx, files):
    """Fetches every declared file and materializes the repository the build sees.

    `files` is a list of `struct(name, url, sha256)`. An empty `sha256` downloads unpinned, which
    only makes sense for a repository whose URLs are derived from versions the checkout owns: such
    a repository is refetched exactly when its artifact changed, so there is no cached copy a
    checksum could have spared us. The manifest records the hash Bazel observed either way, so the
    runtime keeps its content-addressed lookup.

    Returns whether every file was checksum-pinned before the download - which is what makes the
    result reproducible, and the repository shareable through the repo contents cache.
    """
    downloads = []
    for f in files:
        downloads.append(repository_ctx.download(
            url = f.url,
            output = f.name,
            sha256 = f.sha256,
            block = False,
            auth = get_auth(repository_ctx, [f.url]),
        ))

    pinned = True
    rows = []
    for index, download in enumerate(downloads):
        f = files[index]
        if not f.sha256:
            pinned = False
        rows.append("%s\t%s\t%s" % (f.name, download.wait().sha256, f.url))

    repository_ctx.file(
        _PRELOADED_DOWNLOADS_MANIFEST,
        _PRELOADED_DOWNLOADS_MANIFEST_HEADER + "\n" + "\n".join(rows) + "\n",
    )
    names = [f.name for f in files]
    repository_ctx.file(
        "BUILD",
        """
package(default_visibility = ["//visibility:public"])
exports_files([
{exported}
])
filegroup(
    name = "files",
    srcs = [
{artifacts}
    ],
)
""".format(
            exported = ",\n".join(["  \"%s\"" % name for name in names + [_PRELOADED_DOWNLOADS_MANIFEST]]),
            artifacts = "\n".join(["        \"%s\"," % name for name in names]),
        ),
    )
    return pinned

def test_deps_repository(repository_name):
    files = []

    def download_file(name, url, sha256):
        if not name or name.startswith("/") or "\\" in name:
            fail("test_deps_repository requires a non-empty repository-relative name, got: " + name)
        if name == "files" or name == _PRELOADED_DOWNLOADS_MANIFEST:
            fail("test_deps_repository reserves the name '%s' for the repository it generates" % name)
        for part in name.split("/"):
            if not part or part == "." or part == "..":
                fail("test_deps_repository requires a normalized repository-relative name, got: " + name)
        for value in [name, url, sha256]:
            if "\t" in value or "\n" in value or "\r" in value:
                fail("test_deps_repository values must not contain tabs or newlines")
        if len(sha256) != 64:
            fail("test_deps_repository requires a 64-character sha256 for " + name)
        for index in range(len(sha256)):
            char = sha256[index]
            if char not in "0123456789abcdef":
                fail("test_deps_repository requires a lowercase hexadecimal sha256 for " + name)
        for existing in files:
            if existing.name == name:
                fail("test_deps_repository has duplicate file name: " + name)
            if existing.url == url:
                fail("test_deps_repository has duplicate URL: " + url)
        files.append(struct(name = name, url = url, sha256 = sha256))

    def _impl(repository_ctx):
        write_downloads_repo(repository_ctx, files)
        return repository_ctx.repo_metadata(reproducible = True)

    def make_repository_rule():
        return repository_rule(
            implementation = _impl,
        )

    def all_targets():
        return ["@%s//:%s" % (repository_name, f.name) for f in files]

    def manifest_target():
        return "@%s//:%s" % (repository_name, _PRELOADED_DOWNLOADS_MANIFEST)

    return struct(
        repository_name = repository_name,
        download_file = download_file,
        make_repository_rule = make_repository_rule,
        all_targets = all_targets,
        manifest_target = manifest_target,
    )
