"""Declares one crate of the dev-distribution tools, the Cargo workspace in this directory."""

load("@bazel_skylib//rules:diff_test.bzl", "diff_test")
load("@ddt//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load("@rules_rs//rs:rust_binary.bzl", "rust_binary")
load("@rules_rs//rs:rust_library.bzl", "rust_library")
load("@rules_rs//rs:rust_test.bzl", "rust_test")
load("@rules_rust//rust:defs.bzl", "rust_clippy_test", "rust_common")

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@ddt` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it. A `Label` resolves in this repository when the ultimate root
# loads this file.
_LINTS = Label("//build/dev-dist-tools:lints")

# What `@ddt` names as the `lint_config` of a crate whose `Cargo.toml` says `[lints] workspace = true`.
_WORKSPACE_LINTS = "@ddt//:workspace_cargo_lints"

# The crates that a platform-conditional dependency adds on some hosts only. `cargo tree --target <triple>` over the six
# triples of `@ddt` lists them. The crate closure test leaves them out, so one `closure.txt` holds on every host.
_HOST_CRATES = [
    "bitflags",
    "errno",
    "libc",
    "linux_raw_sys",
    "rustix",
    "winapi_util",
    "windows_link",
    "windows_sys",
    "xattr",
]

def _crate_closure_impl(ctx):
    # `transitive_crates` holds the crates that the binary links, and each proc macro that one of them uses directly.
    # A proc macro runs in the compiler and is not linked, so the closure leaves it out.
    names = {
        crate.name: None
        for crate in ctx.attr.binary[rust_common.dep_info].transitive_crates.to_list()
        if "proc-macro" not in (crate.type, crate.wrapped_crate_type) and crate.name not in _HOST_CRATES
    }
    out = ctx.actions.declare_file(ctx.label.name + ".txt")
    ctx.actions.write(out, "".join([crate + "\n" for crate in sorted(names)]))
    return [DefaultInfo(files = depset([out]))]

dev_dist_crate_closure = rule(
    doc = """Writes `<name>.txt`: the rustc names of the crates that `binary` links, sorted, one per line.

    The file leaves out the proc macros and the host-only crates of `_HOST_CRATES`.
    """,
    implementation = _crate_closure_impl,
    attrs = {
        "binary": attr.label(
            doc = "The `rust_binary` of an action tool.",
            mandatory = True,
            providers = [rust_common.dep_info],
        ),
    },
)

def dev_dist_rust_crate(name, test_data = [], closure = False):
    """Declares the crate `<name>`, its unit test `<name>_test`, a test `<name>_<stem>_test` per `tests/<stem>.rs`, and
    the clippy test `<name>-clippy` over all of them.

    `@ddt` supplies the crate name, the edition and the dependencies from `Cargo.toml` and `Cargo.lock`. A crate under
    `bins/` is a `rust_binary` with `src/main.rs`. Any other crate is a `rust_library` with `src/lib.rs`.

    A file `tests/<stem>.rs` of a binary is a Cargo integration test. It gets the dependencies and the dev-dependencies of
    the crate, and the binary as data. The run-time `CARGO_BIN_EXE_<name>` holds the path of the binary. Under Bazel, the
    path is relative to the start directory of the test. A test must make it absolute before it runs the binary in
    another directory.

    The filegroup `<name>_testdata` holds `testdata/`, for the tests of another crate.

    A binary with `closure = True` also gets `<name>_closure` and the test `<name>_closure_test`. The first writes the
    crates that the binary links, and the test compares them with `closure.txt` of the package.

    Args:
      name: the directory name. `@ddt` names a local crate by its package label.
      test_data: more run-time data of the unit test, such as the `<name>_testdata` filegroup of another crate.
      closure: pins the crate closure of an action tool in `closure.txt`. The README states how to regenerate it.
    """
    package = native.package_name()
    if not crate_name():
        fail("Add {} to the members of `Cargo.toml`, then run `cargo build` to update `Cargo.lock`.".format(package))

    # Every crate follows the workspace policy. A crate without `[lints] workspace = true` would build under Bazel with
    # no lints at all, and cargo would still lint it, so the two would disagree unnoticed.
    if lint_config() != _WORKSPACE_LINTS:
        fail("{}: Cargo.toml needs `[lints] workspace = true`".format(package))

    is_binary = package.startswith("build/dev-dist-tools/bins/")
    (rust_binary if is_binary else rust_library)(
        name = name,
        aliases = aliases(),
        crate_name = crate_name(),
        edition = edition(),
        srcs = native.glob(["src/**/*.rs"]),
        deps = all_crate_deps(normal = True),
        lint_config = _LINTS,
        visibility = ["//visibility:public"],
    )

    if closure:
        if not is_binary:
            fail("{}: only a binary under `bins/` has a crate closure test.".format(package))
        dev_dist_crate_closure(name = name + "_closure", binary = ":" + name)
        diff_test(
            name = name + "_closure_test",
            file1 = ":" + name + "_closure",
            file2 = "closure.txt",
            failure_message = ("The crates that {name} links differ from closure.txt. If the change is intended, run " +
                               "`./bazel.cmd build //{package}:{name}_closure` in community/ and copy " +
                               "{name}_closure.txt over closure.txt.").format(name = name, package = package),
        )

    # rules_rust sets the run-time `CARGO_MANIFEST_DIR` to `external/<repo>/<package>`, and the runfiles do not have that
    # path when this module is not the root. Thus a test reads `testdata/` from `DDT_TESTDATA_DIR`. Under `cargo test`, it
    # reads the run-time `CARGO_MANIFEST_DIR`. The process wrapper of rules_rust refuses `env!("CARGO_MANIFEST_DIR")`.
    # A test that uses `include_str!` gets `testdata/` as compile data too.
    # A `rust_test` does not take `lint_config` from its `crate`, so it is passed again.
    repo = native.repo_name()
    testdata = native.glob(["testdata/**"], allow_empty = True)
    native.filegroup(name = name + "_testdata", srcs = testdata, visibility = ["//visibility:public"])
    env = {"DDT_TESTDATA_DIR": ("../{}/".format(repo) if repo else "") + package + "/testdata"}
    rust_test(
        name = name + "_test",
        crate = ":" + name,
        compile_data = testdata,
        data = testdata + test_data,
        deps = all_crate_deps(normal_dev = True),
        env = env,
        lint_config = _LINTS,
    )

    integration_tests = native.glob(["tests/*.rs"], allow_empty = True)
    if integration_tests and not is_binary:
        fail("{} is a library. Only a crate under `bins/` can have an integration test in `tests/`.".format(package))
    integration_test_names = []
    for test in integration_tests:
        test_name = "{}_{}_test".format(name, test.removeprefix("tests/").removesuffix(".rs"))
        integration_test_names.append(test_name)
        rust_test(
            name = test_name,
            aliases = aliases(),
            edition = edition(),
            srcs = [test],
            compile_data = testdata,
            data = [":" + name] + testdata,
            deps = all_crate_deps(normal = True, normal_dev = True),
            env = env | {"CARGO_BIN_EXE_" + name: "$(rootpath :{})".format(name)},
            lint_config = _LINTS,
        )

    # Clippy over this crate only: each crate it depends on has its own `-clippy`. The clippy aspect of rules_rust skips
    # a target of an external repository, and from the ultimate root this module is `external/community+`. So the test
    # runs only when this module is the main repository: `cd community && ./bazel.cmd test //build/dev-dist-tools/...`.
    # From the ultimate root it is incompatible, and `bazel test` reports it as skipped rather than as a pass that
    # checked nothing.
    rust_clippy_test(
        name = name + "-clippy",
        size = "small",
        targets = [":" + name, ":" + name + "_test"] + [":" + test_name for test_name in integration_test_names],
        target_compatible_with = ["@platforms//:incompatible"] if repo else [],
    )

def ddt_crate(name):
    """Returns the label of the third-party crate `name` in `@ddt`, for a Rust target of the ultimate root.

    The ultimate root cannot import `@ddt` with `use_repo`. The rules_rs extension reports the import as indirect, and
    `bazel mod tidy` removes it. A label here resolves `@ddt` through the repository mapping of this module.

    Args:
      name: the crate name, such as `saphyr`. A community crate must depend on it, or `@ddt` does not have it.
    """
    return Label("@ddt//:" + name)
