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

def test_deps_repository(repository_name):
    files = []

    def download_file(name, url, sha256):
        files.append(struct(name = name, url = url, sha256 = sha256))

    def _impl(repository_ctx):
        # Download all declared files in parallel
        downloads = []
        for f in files:
            downloads.append(repository_ctx.download(
                url = f.url,
                output = f.name,
                sha256 = f.sha256,
                block = False,
                auth = get_auth(repository_ctx, [f.url])
            ))
        for d in downloads:
            d.wait()

        repository_ctx.file(
            "BUILD",
            """
package(default_visibility = ["//visibility:public"]) 
exports_files([
{files}
])
""".format(files = ",\n".join(["  \"%s\"" % f.name for f in files]))
        )

    def make_repository_rule():
        return repository_rule(
            implementation = _impl,
            local = True,
        )

    def all_targets():
        return ["@%s//:%s" % (repository_name, f.name) for f in files]

    return struct(
        repository_name = repository_name,
        download_file = download_file,
        make_repository_rule = make_repository_rule,
        all_targets = all_targets,
    )
