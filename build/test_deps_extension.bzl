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

load("@bazel_tools//tools/build_defs/repo:cache.bzl", "DEFAULT_CANONICAL_ID_ENV")
load(":download_rules.bzl", "MANIFEST_NAME", "write_downloads_repo")

def test_deps_repository(repository_name, remote_cacheable = True):
    """Creates a test dependency repository.

    Args:
        repository_name: The repository name.
        remote_cacheable: Whether pinned repository contents can use the remote cache.
    """
    files = []

    def download_file(name, url, sha256):
        if not name or name.startswith("/") or "\\" in name:
            fail("test_deps_repository requires a non-empty repository-relative name, got: " + name)
        if name == "files" or name == MANIFEST_NAME:
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
        # Taken from the fetch rather than asserted: `download_file` requires a checksum, so this is true
        # today, and it stays true by construction if that ever relaxes
        pinned = write_downloads_repo(repository_ctx, files)
        return repository_ctx.repo_metadata(reproducible = pinned and remote_cacheable)

    def make_repository_rule():
        return repository_rule(
            implementation = _impl,
            environ = [DEFAULT_CANONICAL_ID_ENV],
        )

    def all_targets():
        return ["@%s//:%s" % (repository_name, f.name) for f in files]

    def manifest_target():
        return "@%s//:%s" % (repository_name, MANIFEST_NAME)

    return struct(
        repository_name = repository_name,
        download_file = download_file,
        make_repository_rule = make_repository_rule,
        all_targets = all_targets,
        manifest_target = manifest_target,
    )
