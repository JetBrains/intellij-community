# rust-lints

The one Rust lint policy of the build tools: the dev-dist tools in `community/build/dev-dist-tools` and
`build/dev-dist-tools`, and the Air UI-lane tooling in `plugins/air/tests/integration/vm-lane`.

## What is shared and what is copied

Cargo inherits `[lints]` inside one workspace only. There is no include across workspaces, and rules_rs reads the
tables from the workspace `Cargo.toml`, not from `.cargo/config.toml`. So each workspace root `Cargo.toml` carries a
copy of the two tables between two marker lines. The shared place holds:

- `lints.toml`: the source of both tables, with a one-line reason for every `allow`.
- `sync.mjs`: writes the tables into every listed workspace manifest. `--check` names a copy that differs and writes
  nothing. The list is `manifestPaths` in the script. A manifest outside the checkout is skipped with a note.
- `defs.bzl`: `rust_lints_as_errors`, which appends `-Dwarnings` to the clippy flags of a rendered table, and
  `rust_lints_equal_check`, which fails when two crate hubs render different tables.

## Commands

```sh
bun community/build/rust-lints/sync.mjs            # write the copies
bun community/build/rust-lints/sync.mjs --check    # exit 1 when a copy differs
node --test community/build/rust-lints/sync.test.mjs
```

Run the check after any edit of `lints.toml` or of a `[workspace.lints]` table. The Cargo edit-test loop is
`cargo clippy --all-targets` in the workspace directory; Bazel runs the same policy through the `<crate>-clippy`
tests of each workspace.

## How a workspace joins

1. Put the two marker lines from `sync.mjs` into the workspace `Cargo.toml` where the tables belong, add the
   manifest to `manifestPaths`, and run the script.
2. Give every member `Cargo.toml` a `[lints]` table with `workspace = true`.
3. Set `generate_lint_config = True` on the `crate.from_cargo` tag of the workspace hub. The hub then renders
   `@<hub>//:workspace_cargo_lints`.
4. Declare `rust_lints_as_errors(name = "lints", cargo = "@<hub>//:workspace_cargo_lints")` in the workspace
   package. Pass it as `lint_config` to every `rust_library`, `rust_binary` and `rust_test`, and declare one
   `rust_clippy_test` per crate over those targets. `community/build/dev-dist-tools/defs.bzl` shows the pattern.
5. The clippy aspect of rules_rust skips a target of an external repository, and a `rust_clippy_test` over such
   targets passes without a check. A crate of the community module is external from the ultimate root, so its
   clippy test must run from `community/`. Mark the test incompatible when `native.repo_name()` is not empty, so
   that `bazel test` reports a skip and not a pass.

## The rules of the table

- `clippy::pedantic` is on as a group. A pedantic lint the workspaces do not follow gets an `allow` with a
  one-line reason in `lints.toml`.
- `nursery` and `restriction` are opted into lint by lint. The groups change between releases, the Bazel
  toolchain and a local cargo can differ by a few lints, and an unknown lint name under `-Dwarnings` fails the build.
- A site-local exception is `#[expect(lint, reason = "...")]`, never a bare `#[allow]`:
  `allow_attributes_without_reason` is on.

## Next step

When every hub has `generate_lint_config`, declare a `rust_lints_equal_check` over their `workspace_cargo_lints`
targets in the ultimate root, so `bazel build` catches a copy that drifted.
