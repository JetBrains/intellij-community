"""Declares one crate of the dev-distribution tools, the Cargo workspace in this directory."""

load("@ddt//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load("//build/rust-tools:defs.bzl", "rust_tool_crate", "rust_tool_hub")

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@ddt` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it. A `Label` resolves in this repository when the ultimate root
# loads this file.
_LINTS = Label("//build/dev-dist-tools:lints")

_HUB = rust_tool_hub(
    aliases = aliases,
    all_crate_deps = all_crate_deps,
    crate_name = crate_name,
    edition = edition,
    lint_config = lint_config,
    workspace_lints = "@ddt//:workspace_cargo_lints",
)

def dev_dist_rust_crate(name, test_data = [], closure = False):
    """Declares the crate `<name>` of this workspace with `rust_tool_crate` of `community/build/rust-tools/defs.bzl`.

    The targets are the library or the binary, `<name>_test`, a test `<name>_<stem>_test` per `tests/<stem>.rs` of a
    binary, the filegroup `<name>_testdata`, and `<name>-clippy`. A crate under `bins/` is a binary. A test reads
    `testdata/` from `DDT_TESTDATA_DIR` under Bazel, and from the run-time `CARGO_MANIFEST_DIR` under `cargo test`.

    Args:
      name: the directory name. `@ddt` names a local crate by its package label.
      test_data: more run-time data of the unit test, such as the `<name>_testdata` filegroup of another crate.
      closure: pins the crate closure of an action tool in `closure.txt`. The README states how to regenerate it.
    """
    rust_tool_crate(
        name = name,
        hub = _HUB,
        lints = _LINTS,
        bins_prefix = "build/dev-dist-tools/bins/",
        test_data = test_data,
        testdata_env = "DDT_TESTDATA_DIR",
        closure = closure,
    )

def ddt_crate(name):
    """Returns the label of the third-party crate `name` in `@ddt`, for a Rust target of the ultimate root.

    The ultimate root cannot import `@ddt` with `use_repo`. The rules_rs extension reports the import as indirect, and
    `bazel mod tidy` removes it. A label here resolves `@ddt` through the repository mapping of this module.

    Args:
      name: the crate name, such as `saphyr`. A community crate must depend on it, or `@ddt` does not have it.
    """
    return Label("@ddt//:" + name)
