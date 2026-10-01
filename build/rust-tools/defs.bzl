"""The shared Bazel rules of the Rust tool workspaces. `README.md` is the spec that names what each workspace has."""

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
