"""The shared Bazel rules of the Rust tool workspaces. `README.md` is the spec that names what each workspace has.

The crate macro of a workspace is a thin binding of `rust_tool_crate`. Starlark cannot load a file by a name that is
known only at run time, so the `defs.bzl` of the workspace loads the functions of its crate hub and passes them in
through `rust_tool_hub`.
"""

load("@bazel_skylib//rules:diff_test.bzl", "diff_test")
load("@rules_rs//rs:rust_binary.bzl", "rust_binary")
load("@rules_rs//rs:rust_library.bzl", "rust_library")
load("@rules_rs//rs:rust_test.bzl", "rust_test")
load("@rules_rust//rust:defs.bzl", "rust_clippy_test", "rust_common")

# No public file exports `LintsInfo`; rules_rs `cargo_lints.bzl` loads it from here too.
load("@rules_rust//rust/private:providers.bzl", "LintsInfo")

def _rust_lints_as_errors_impl(ctx):
    cargo = ctx.attr.cargo[LintsInfo]
    return [LintsInfo(
        rustc_lint_flags = cargo.rustc_lint_flags,
        rustc_lint_files = cargo.rustc_lint_files,
        clippy_lint_flags = cargo.clippy_lint_flags + ["-Dwarnings"],
        clippy_lint_files = cargo.clippy_lint_files,
        rustdoc_lint_flags = cargo.rustdoc_lint_flags,
        rustdoc_lint_files = cargo.rustdoc_lint_files,
    )]

rust_lints_as_errors = rule(
    doc = """The lint policy of a Cargo workspace, with every clippy warning an error.

    rules_rust passes `-Dwarnings` to clippy only when no `lint_config` gives a flag, so the policy of
    `[workspace.lints]` alone would turn clippy's warnings into a passing, cached test. The flag is added here
    rather than through `--@rules_rust//rust/settings:clippy_flag`, which is repository-wide. rustc gets the
    policy as it is: `community/common.bazelrc` already makes a rustc warning an error, and `deny` lints such as
    `unsafe_op_in_unsafe_fn` fail it on their own.

    Pass the result as `lint_config` of every `rust_library`, `rust_binary` and `rust_test` of the workspace, and
    name those targets in a `rust_clippy_test`.""",
    implementation = _rust_lints_as_errors_impl,
    attrs = {
        "cargo": attr.label(
            doc = "The `workspace_cargo_lints` target of the crate hub: `[workspace.lints]` as rules_rs renders it.",
            mandatory = True,
            providers = [LintsInfo],
        ),
    },
)

# The rendered `[workspace.lints]` of the community workspaces: the dev-dist tools (`@ddt`) and BT (`@bt`). A `Label`
# resolves each hub through the repository mapping of this module, so the ultimate root can name them too.
COMMUNITY_WORKSPACE_LINTS = [
    Label("@ddt//:workspace_cargo_lints"),
    Label("@bt//:workspace_cargo_lints"),
]

_LINT_FIELDS = ["rustc_lint_flags", "clippy_lint_flags", "rustdoc_lint_flags"]

def _rust_lints_equal_check_impl(ctx):
    first = ctx.attr.targets[0]
    for target in ctx.attr.targets[1:]:
        for field in _LINT_FIELDS:
            if getattr(first[LintsInfo], field) != getattr(target[LintsInfo], field):
                fail("{} and {} differ in {}. Run `bun community/build/rust-tools/sync.mjs`.".format(first.label, target.label, field))
    marker = ctx.actions.declare_file(ctx.label.name + ".ok")
    ctx.actions.write(marker, "")
    return [DefaultInfo(files = depset([marker]))]

rust_lints_equal_check = rule(
    doc = """Fails the analysis when two rendered `[workspace.lints]` tables differ.

    Each Cargo workspace carries a copy of `lints.toml`, because Cargo inherits `[lints]` inside one workspace only.
    `sync.mjs --check` compares the copies as text. This rule compares what Bazel reads: the `workspace_cargo_lints`
    target of each crate hub. A `bazel build` of it is the check; it writes an empty marker file.""",
    implementation = _rust_lints_equal_check_impl,
    attrs = {
        "targets": attr.label_list(
            doc = "The `workspace_cargo_lints` targets to compare, two or more.",
            mandatory = True,
            providers = [LintsInfo],
        ),
    },
)

def rust_tool_hub(aliases, all_crate_deps, crate_name, edition, lint_config, workspace_lints, dep_data = None):
    """Binds `rust_tool_crate` to one crate hub of rules_rs.

    The first five arguments are the functions of the same name in `@<hub>//:defs.bzl`.

    Args:
      aliases: `aliases` of the hub.
      all_crate_deps: `all_crate_deps` of the hub.
      crate_name: `crate_name` of the hub.
      edition: `edition` of the hub.
      lint_config: `lint_config` of the hub.
      workspace_lints: what the hub names as the `lint_config` of a crate with `[lints] workspace = true`, such as
        `"@ddt//:workspace_cargo_lints"`.
      dep_data: `DEP_DATA` of `@<hub>//:data.bzl`. Only `cross_module_crates` of `rust_tool_crate` reads it.

    Returns:
      The hub, for `rust_tool_crate`.
    """
    return struct(
        aliases = aliases,
        all_crate_deps = all_crate_deps,
        crate_name = crate_name,
        edition = edition,
        lint_config = lint_config,
        workspace_lints = workspace_lints,
        dep_data = dep_data,
    )

def _cross_module(dep, cross_module_crates):
    # A hub names a crate of another workspace `@<hub>//:<package name>-<version>`.
    repo, separator, target = dep.partition("//:")
    if not separator or not repo.startswith("@"):
        return dep
    return cross_module_crates.get(target.rpartition("-")[0], dep)

def _cross_module_deps(dep_data, kinds, cross_module_crates):
    """What `all_crate_deps` of a hub returns for `kinds` of `dep_data`, with `cross_module_crates` substituted.

    That function returns a list plus a `select`, which a macro cannot rewrite, so the merge is done here from the
    `DEP_DATA` entry. A dependency every platform shares stays out of the `select`, because a label in both fails.
    """
    shared = {}
    by_platform = {}
    for kind in kinds:
        for dep in dep_data.get(kind, []):
            shared[_cross_module(dep, cross_module_crates)] = True
        for platform, deps in dep_data.get(kind + "_by_platform", {}).items():
            branch = by_platform.setdefault(platform, {})
            for dep in deps:
                branch[_cross_module(dep, cross_module_crates)] = True
    branches = {}
    for platform, deps in sorted(by_platform.items()):
        only_here = sorted([dep for dep in deps if dep not in shared])
        if only_here:
            branches[platform] = only_here
    if not branches:
        return sorted(shared)
    branches["//conditions:default"] = []
    return sorted(shared) + select(branches)

# The crates that a platform-conditional dependency adds on some hosts only. `cargo tree --target <triple>` over the
# triples of a hub lists them. The crate closure test leaves them out, so one `closure.txt` holds on every host.
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

rust_crate_closure = rule(
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

def rust_tool_crate(
        name,
        hub,
        lints,
        bins_prefix,
        cross_module_crates = {},
        test_data = [],
        compile_data = [],
        test_sharding = False,
        testdata_env = None,
        closure = False,
        integration_tests = True):
    """Declares one crate of a Rust tool workspace, its tests, and the clippy test over all of them.

    The hub supplies the crate name, the edition and the dependencies from `Cargo.toml` and `Cargo.lock`. A crate whose
    package starts with `bins_prefix` is a `rust_binary` with `src/main.rs`. Any other crate is a `rust_library` with
    `src/lib.rs`. The targets:

    - `<name>`: the library or the binary.
    - `<name>_test`: the unit test.
    - `<name>_<stem>_test` per `tests/<stem>.rs` of a binary, when `integration_tests` is set. Such a file is a Cargo
      integration test. It gets the dependencies and the dev-dependencies of the crate, and the binary as data. The
      run-time `CARGO_BIN_EXE_<name>` holds the path of the binary. Under Bazel, the path is relative to the start
      directory of the test. A test must make it absolute before it runs the binary in another directory.
    - `<name>_testdata`, when `testdata_env` is set: a filegroup of `testdata/`, for the tests of another crate.
    - `<name>_closure` and `<name>_closure_test`, when `closure` is set. The first writes the crates that the binary
      links, and the test compares them with `closure.txt` of the package.
    - `<name>-clippy`: clippy over the crate and its tests. It runs only when this module is the main repository.

    Args:
      name: the directory name. The hub names a local crate by its package label.
      hub: the crate hub, from `rust_tool_hub`.
      lints: the `rust_lints_as_errors` target of the workspace. Every target gets it as `lint_config`.
      bins_prefix: the package prefix of the binaries, such as `"build/dev-dist-tools/bins/"`.
      cross_module_crates: `{package name: label}` of the crates of another workspace that the crate links through a
        path dependency. The hub would build such a crate a second time, without the lint policy. The label names the
        target of the module that builds it already. It needs `dep_data` of the hub.
      test_data: more run-time data of the unit test, such as the `<name>_testdata` filegroup of another crate.
      compile_data: the files that the crate reads at compile time (`include_str!`). Its unit test gets them as
        run-time data too.
      test_sharding: lets the rules_rust wrapper split the libtest cases across the shards of the unit test.
      testdata_env: the environment variable that gives a test the path of `testdata/`. The test also gets
        `testdata/` as compile data and as run-time data.
      closure: pins the crate closure of an action tool in `closure.txt`. Only a binary can have it.
      integration_tests: declares a test per `tests/*.rs` of a binary.
    """
    package = native.package_name()
    if not hub.crate_name():
        fail("Add {} to the members of `Cargo.toml`, then run `cargo build` to update `Cargo.lock`.".format(package))

    # Every crate follows the workspace policy. A crate without `[lints] workspace = true` would build under Bazel with
    # no lints at all, and cargo would still lint it, so the two would disagree unnoticed.
    if hub.lint_config() != hub.workspace_lints:
        fail("{}: Cargo.toml needs `[lints] workspace = true`".format(package))

    aliases = hub.aliases()
    if cross_module_crates:
        if hub.dep_data == None:
            fail("{}: `cross_module_crates` needs `dep_data` in `rust_tool_hub`".format(package))
        dep_data = hub.dep_data[package]
        deps = _cross_module_deps(dep_data, ["deps"], cross_module_crates)
        test_deps = _cross_module_deps(dep_data, ["dev_deps"], cross_module_crates)
        integration_test_deps = _cross_module_deps(dep_data, ["deps", "dev_deps"], cross_module_crates)
        aliases = {_cross_module(dep, cross_module_crates): alias for dep, alias in aliases.items()}
    else:
        deps = hub.all_crate_deps(normal = True)
        test_deps = hub.all_crate_deps(normal_dev = True)
        integration_test_deps = hub.all_crate_deps(normal = True, normal_dev = True)

    _rust_tool_targets(
        name = name,
        is_binary = package.startswith(bins_prefix),
        crate_attrs = {"aliases": aliases, "crate_name": hub.crate_name(), "edition": hub.edition()},
        lints = lints,
        deps = deps,
        test_deps = test_deps,
        integration_test_deps = integration_test_deps,
        compile_data = compile_data,
        test_data = test_data,
        test_sharding = test_sharding,
        testdata_env = testdata_env,
        closure = closure,
        integration_tests = integration_tests,
    )

def rust_tool_binary(name, deps, lints, edition, test_deps = [], testdata_env = None):
    """Declares one binary of a Rust tool workspace that Bazel builds without a crate hub, its unit test, and its clippy.

    Bazel does not read `Cargo.toml` of such a workspace, so `deps` repeats `[dependencies]` of the crate, and
    `test_deps` repeats `[dev-dependencies]`. The unit test gets `deps` from the binary, and `test_deps` in addition.
    The targets are `<name>` with `src/main.rs`, `<name>_test`, `<name>-clippy`, and `<name>_testdata` when
    `testdata_env` is set. `rust_tool_crate` describes each.

    Args:
      name: the directory name.
      deps: the dependencies of the binary.
      lints: the `rust_lints_as_errors` target. Every target gets it as `lint_config`.
      edition: the Rust edition of the crate.
      test_deps: the dependencies of the unit test only.
      testdata_env: the environment variable that gives the test the path of `testdata/`.
    """
    _rust_tool_targets(
        name = name,
        is_binary = True,
        crate_attrs = {"edition": edition},
        lints = lints,
        deps = deps,
        test_deps = test_deps,
        integration_test_deps = [],
        compile_data = [],
        test_data = [],
        test_sharding = False,
        testdata_env = testdata_env,
        closure = False,
        integration_tests = False,
    )

def _rust_tool_targets(
        name,
        is_binary,
        crate_attrs,
        lints,
        deps,
        test_deps,
        integration_test_deps,
        compile_data,
        test_data,
        test_sharding,
        testdata_env,
        closure,
        integration_tests):
    package = native.package_name()
    repo = native.repo_name()
    (rust_binary if is_binary else rust_library)(
        name = name,
        srcs = native.glob(["src/**/*.rs"]),
        compile_data = compile_data,
        deps = deps,
        lint_config = lints,
        visibility = ["//visibility:public"],
        **crate_attrs
    )

    if closure:
        if not is_binary:
            fail("{}: only a binary has a crate closure test.".format(package))
        rust_crate_closure(name = name + "_closure", binary = ":" + name)
        diff_test(
            name = name + "_closure_test",
            file1 = ":" + name + "_closure",
            file2 = "closure.txt",
            failure_message = ("The crates that {name} links differ from closure.txt. If the change is intended, run " +
                               "`./bazel.cmd build //{package}:{name}_closure` in the root of this module and copy " +
                               "{name}_closure.txt over closure.txt.").format(name = name, package = package),
        )

    # rules_rust sets the run-time `CARGO_MANIFEST_DIR` to `external/<repo>/<package>`, and the runfiles do not have that
    # path when this module is not the root. Thus a test reads `testdata/` from `testdata_env`. Under `cargo test`, it
    # reads the run-time `CARGO_MANIFEST_DIR`. The process wrapper of rules_rust refuses `env!("CARGO_MANIFEST_DIR")`.
    # A test that uses `include_str!` gets `testdata/` as compile data too.
    testdata = []
    env = {}
    if testdata_env:
        testdata = native.glob(["testdata/**"], allow_empty = True)
        native.filegroup(name = name + "_testdata", srcs = testdata, visibility = ["//visibility:public"])
        env = {testdata_env: ("../{}/".format(repo) if repo else "") + package + "/testdata"}

    # A `rust_test` does not take `lint_config` from its `crate`, so it is passed again. With `test_sharding`, the
    # rules_rust wrapper splits the libtest cases across the shards. A lane that runs a test with
    # `--test_sharding_strategy=forced=2` fails a runner that does not report sharding.
    rust_test(
        name = name + "_test",
        crate = ":" + name,
        compile_data = compile_data + testdata,
        data = compile_data + testdata + test_data,
        deps = test_deps,
        env = env,
        experimental_enable_sharding = test_sharding,
        lint_config = lints,
    )

    integration_test_names = []
    if integration_tests:
        tests = native.glob(["tests/*.rs"], allow_empty = True)
        if tests and not is_binary:
            fail("{} is a library. Only a binary can have an integration test in `tests/`.".format(package))
        integration_attrs = {key: value for key, value in crate_attrs.items() if key != "crate_name"}
        for test in tests:
            test_name = "{}_{}_test".format(name, test.removeprefix("tests/").removesuffix(".rs"))
            integration_test_names.append(test_name)
            rust_test(
                name = test_name,
                srcs = [test],
                compile_data = compile_data + testdata,
                data = [":" + name] + compile_data + testdata,
                deps = integration_test_deps,
                env = env | {"CARGO_BIN_EXE_" + name: "$(rootpath :{})".format(name)},
                lint_config = lints,
                **integration_attrs
            )

    # Clippy over this crate only: each crate it depends on has its own `-clippy`. The clippy aspect of rules_rust skips
    # a target of an external repository, and from the ultimate root a community crate is one. So the test runs only
    # when this module is the main repository. Elsewhere it is incompatible, and `bazel test` reports it as skipped
    # rather than as a pass that checked nothing.
    rust_clippy_test(
        name = name + "-clippy",
        size = "small",
        targets = [":" + name, ":" + name + "_test"] + [":" + test_name for test_name in integration_test_names],
        target_compatible_with = ["@platforms//:incompatible"] if repo else [],
    )

_COMPILATION_MODE = "//command_line_option:compilation_mode"
_PLATFORMS = "//command_line_option:platforms"

def _optimized_transition_impl(settings, attr):
    return {
        _COMPILATION_MODE: "opt",
        _PLATFORMS: [str(attr.platform)] if attr.platform else settings[_PLATFORMS],
    }

# A shipped binary is always optimized. `bazel run` and a lane's runfiles build in fastbuild, which for Rust is
# `-Copt-level=0` for every crate and every dependency: the hashing, the zip deflate, the WebP stills and the X11 frame
# copies would then run ten times slower or worse.
_optimized_transition = transition(
    implementation = _optimized_transition_impl,
    inputs = [_PLATFORMS],
    outputs = [_COMPILATION_MODE, _PLATFORMS],
)

def _optimized_binary_impl(ctx):
    binary = ctx.attr.binary[0][DefaultInfo]
    executable = binary.files_to_run.executable
    extension = "." + executable.extension if executable.extension else ""
    path = ctx.label.name + "/" + ctx.attr.binary_name if ctx.attr.binary_name else ctx.label.name
    output = ctx.actions.declare_file(path + extension)
    ctx.actions.symlink(output = output, target_file = executable, is_executable = True)
    return [DefaultInfo(
        executable = output,
        files = depset([output]),
        runfiles = ctx.runfiles([output]).merge(binary.default_runfiles),
    )]

optimized_binary = rule(
    doc = """One shipped binary, optimized, for the host or for one named platform.

    Its only file is the binary. `bazel run --script_path` (the wrappers), `$(rlocationpath)` (`ui_lane_ide.bzl`), the
    controller's `cquery ... [0]` for the guest agent, and `bazel-build-run.cmd` each need exactly one. The test of the
    binary stays on the `rust_binary` in the configuration of the build. The Air UI-lane workspace loads this rule as
    `avl_binary`.""",
    implementation = _optimized_binary_impl,
    executable = True,
    attrs = {
        "binary": attr.label(mandatory = True, executable = True, cfg = _optimized_transition),
        "binary_name": attr.string(
            doc = "The file name of the binary, without the extension. When set, the file is `<name>/<binary_name>`, " +
                  "so that a wrapper runs the binary under its own name. When unset, the file is `<name>`.",
        ),
        "platform": attr.label(doc = "The platform to build for; the host's when unset."),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

_WINDOWS_PLATFORMS = {
    "arm64": "@rules_rs//rs/platforms:aarch64-pc-windows-msvc",
    "x86_64": "@rules_rs//rs/platforms:x86_64-pc-windows-msvc",
}

def windows_clippy_tests(targets, compile_checks = {}):
    """Declares `clippy-windows-x86_64` and `clippy-windows-arm64`: clippy over `targets` and the crates they link.

    A macOS or Linux host never compiles the `cfg(windows)` code. These tests lint it from such a host, one test per
    Windows platform. They only check, so they need no linker for the platform. A Windows host lints that code in the
    `<crate>-clippy` tests, so there these tests are incompatible. They also run only when this module is the main
    repository, because the clippy aspect skips a target of an external repository.

    Args:
      targets: the binaries and the libraries to lint, with the crates that they link.
      compile_checks: `{name: binary}`. Each entry declares `<name>-windows-x86_64` and `<name>-windows-arm64`: the
        binary, optimized for that platform. They are compile checks that a person builds by hand (`manual`).
    """
    for arch, platform in _WINDOWS_PLATFORMS.items():
        for name, binary in compile_checks.items():
            optimized_binary(
                name = "{}-windows-{}".format(name, arch),
                binary = binary,
                platform = platform,
                tags = ["manual"],
            )
        rust_clippy_test(
            name = "clippy-windows-" + arch,
            size = "small",
            platform = platform,
            target_compatible_with = select({
                "@platforms//os:windows": ["@platforms//:incompatible"],
                "//conditions:default": [],
            }) + (["@platforms//:incompatible"] if native.repo_name() else []),
            targets = targets,
            transitive = True,
        )
