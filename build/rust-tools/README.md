# rust-tools

The shared configuration of the Rust tool workspaces, and the spec of what each workspace has. Three of the four Cargo
workspaces follow it. The Air UI-lane tooling joins at the vm lane rework.

| Workspace | Crate hub | Crate macro |
|---|---|---|
| `community/build/dev-dist-tools`: the dev-dist tools | `@ddt` | `dev_dist_rust_crate` |
| `build/dev-dist-tools`: the ultimate dev-dist tools | the crates of `@ddt` | `dev_dist_rust_binary` |
| `community/tools/bt`: BT | `@bt` | `bt_rust_crate` |
| `plugins/air/tests/integration/vm-lane`: the Air UI-lane tooling | `@avl` | `avl_crate` |

## The files here

- `lints.toml`: the lint policy, the source of the two `[workspace.lints]` tables. Every `allow` has a one-line
  reason.
- `rustfmt.toml`: the format, with the repository width of 140 columns.
- `clippy.toml`: the banned methods, each with its replacement.
- `sync.mjs`: writes the tables into every workspace `Cargo.toml` of `manifestPaths`, and writes a copy of
  `rustfmt.toml` and `clippy.toml` into the directory of each such manifest. The first line of a copy names its
  source. `--check` names a copy that differs or is missing, and writes nothing. A manifest outside the checkout is
  skipped with a note, and so is a workspace of `optedOutManifests`.
- `defs.bzl`: the lint rules and the macro core. `rust_lints_as_errors` appends `-Dwarnings` to the clippy flags of a
  rendered table. `rust_lints_equal_check` fails when two crate hubs render different tables. The macro core is the
  next section.

Cargo inherits `[lints]` inside one workspace only. There is no include across workspaces, and rules_rs reads the
tables from the workspace `Cargo.toml`, not from `.cargo/config.toml`. rustfmt and clippy also find `rustfmt.toml` and
`clippy.toml` only in the crate directory and its parents. So each workspace carries copies, and `sync.mjs` keeps them
equal to the sources.

## What every Rust tool workspace has

1. The two lint tables between the marker lines of `sync.mjs` in the workspace `Cargo.toml`.
2. `[lints] workspace = true` in every member `Cargo.toml`. The crate macro fails without it.
3. The synced `rustfmt.toml` and `clippy.toml` in the workspace directory.
4. A crate hub with `generate_lint_config = True` on its `crate.from_cargo` tag. The hub then renders
   `@<hub>//:workspace_cargo_lints`.
5. `rust_lints_as_errors(name = "lints", cargo = "@<hub>//:workspace_cargo_lints")` in the workspace package. Every
   `rust_library`, `rust_binary` and `rust_test` gets it as `lint_config`.
6. One `<crate>-clippy` test per crate, over the crate and its tests.
7. A Windows clippy target set: `windows_clippy_tests` over the binaries, one test per Windows platform, run from a
   macOS or Linux host. It lints the `cfg(windows)` code that the host never compiles.
8. A closure test for every Bazel action tool: `<bin>_closure_test` compares the crates that the tool links with its
   `closure.txt`. `rust_tool_crate` declares it with `closure = True`.
9. A `README.md` with the crate map and the gates.
10. An `API.md` for each crate under the subset rule, with a table of the input that the crate refuses.

### Where each workspace stands

| Workspace | Gaps |
|---|---|
| dev-dist tools | None. |
| BT | None. BT has no Bazel action tool, so it has no closure test. |
| Air UI-lane tooling | Opted out of `sync.mjs` until the vm lane rework. Its tables are a hand copy, which `lints_equal_test` of the ultimate root compares with the others. It has no `rustfmt.toml` or `clippy.toml` copy, the sources use the width of 100 columns, and the code calls the banned methods at about 30 sites. `avl_lints` is a copy of `rust_lints_as_errors`, `avl_crate` does not bind the macro core, and `avl.bzl` loads `optimized_binary` through the BT `defs.bzl`. |

## The Bazel macro core

`defs.bzl` declares the targets of every crate, so the workspaces do not each carry a copy. The doc string of each
declaration states its arguments.

- `rust_tool_hub` binds the core to one crate hub. Starlark cannot load a file by a name that is known only at run
  time, so the `defs.bzl` of a workspace loads the functions of its hub and passes them in.
- `rust_tool_crate` declares one crate: the library or the binary, its unit test, an integration test per
  `tests/*.rs` of a binary, the `testdata/` filegroup, the closure test, and `<crate>-clippy`.
- `rust_tool_binary` declares one binary of a workspace that Bazel builds without a hub, from explicit dependencies.
- `rust_crate_closure` writes the crate closure of a binary. `_HOST_CRATES` lists the crates that it leaves out.
- `optimized_binary` gives one shipped binary in `opt`, for the host or for one platform. The test stays on the
  `rust_binary` of the build configuration.
- `windows_clippy_tests` declares `clippy-windows-x86_64` and `clippy-windows-arm64`, and optional compile checks.

Each workspace keeps a thin binding with its own signature:

| Binding | Binds | Passes |
|---|---|---|
| `dev_dist_rust_crate(name, test_data, closure)` | `rust_tool_crate` | the `@ddt` hub, `:lints`, `bins_prefix = "build/dev-dist-tools/bins/"`, `testdata_env = "DDT_TESTDATA_DIR"` |
| `bt_rust_crate(name, compile_data)` | `rust_tool_crate` | the `@bt` hub, `:lints`, `bins_prefix = "tools/bt/bins/"`, `test_sharding = True` |
| `dev_dist_rust_binary(name, deps, test_deps)` | `rust_tool_binary` | the community `:lints`, `edition = "2024"`, `testdata_env = "DDT_TESTDATA_DIR"` |

## The checks

```sh
bun community/build/rust-tools/sync.mjs --check            # exit 1 when a copy differs or is missing
node --test community/build/rust-tools/sync.test.mjs
cd community && ./bazel.cmd test //build/rust-tools/...     # the tables of @ddt and @bt are equal
./bazel.cmd test //build/dev-dist-tools:lints_equal_test     # from the ultimate root: @ddt, @bt and @avl
cargo fmt --check && cargo clippy --all-targets             # in each workspace directory of manifestPaths
```

The Windows check of a workspace is `bazel test` of its `windows_clippy_tests` targets, plus a cross-target cargo
clippy. The cargo command lints the tests too, which the Bazel targets do not:

```sh
RUSTC_BOOTSTRAP=1 cargo clippy -Zbuild-std=std,panic_abort --target x86_64-pc-windows-msvc --workspace --all-targets
```

Both only check the code. They run no test on Windows, and the CI only builds there.

Run `sync.mjs` after an edit of a source file here, then run the checks. The Cargo edit-test loop is
`cargo clippy --all-targets` in the workspace directory. Bazel runs the same policy through the `<crate>-clippy` tests
of each workspace.

`rust_lints_equal_check` compares what Bazel reads, the rendered tables of the hubs. `sync.mjs --check` compares the
text of the copies. `lints_equal_test` wraps each check in a `build_test`, so a `bazel test --build_tests_only` run
analyzes it too.

`community/.bazelrc` gives `clippy.toml` to the Bazel clippy aspect of the community root. The ultimate root sets no
clippy configuration, because the setting applies to every clippy test there, and the Air UI-lane tooling calls the
banned methods. So the bans apply under Bazel to the community crates only, and under cargo to every workspace that
has the copy.

## How a workspace joins

1. Put the two marker lines from `sync.mjs` into the workspace `Cargo.toml` where the tables belong, add the
   manifest to `manifestPaths`, and run the script. It writes the tables, `rustfmt.toml` and `clippy.toml`. A workspace
   of `optedOutManifests` leaves that list in the same change.
2. Give every member `Cargo.toml` a `[lints]` table with `workspace = true`.
3. Set `generate_lint_config = True` on the `crate.from_cargo` tag of the workspace hub.
4. Declare `rust_lints_as_errors(name = "lints", cargo = "@<hub>//:workspace_cargo_lints")` in the workspace
   package. Bind `rust_tool_crate` to the hub in the workspace `defs.bzl` with `rust_tool_hub`, and pass `:lints`.
   `community/tools/bt/defs.bzl` shows the pattern. Declare `windows_clippy_tests` over the binaries in the workspace
   package.
5. Run the clippy tests of a community workspace from `community/`. The clippy aspect of rules_rust skips a target
   of an external repository, and a `rust_clippy_test` over such targets passes without a check. A crate of the
   community module is external from the ultimate root. So the core marks each clippy test incompatible when
   `native.repo_name()` is not empty, and `bazel test` reports a skip and not a pass.
6. Add `@<hub>//:workspace_cargo_lints` to the targets of a `rust_lints_equal_check`. A community hub goes into
   `COMMUNITY_WORKSPACE_LINTS` of `defs.bzl`. An ultimate hub goes into `//build/dev-dist-tools:lints_equal_check` of
   the ultimate root.
7. Run `cargo fmt` once over the workspace, in a commit without other changes.

## The idioms

The [Rust Code Style](../../.agents/skills/rust-code-style/SKILL.md) skill states how the code of a workspace is
written: the error model and the command line by audience, the crate rule, the test layout, and the versions.

## The rules of the table

- `clippy::pedantic` is on as a group. A pedantic lint the workspaces do not follow gets an `allow` with a
  one-line reason in `lints.toml`.
- `nursery` and `restriction` are opted into lint by lint. The groups change between releases, the Bazel
  toolchain and a local cargo can differ by a few lints, and an unknown lint name under `-Dwarnings` fails the build.
- A site-local exception is `#[expect(lint, reason = "...")]`, never a bare `#[allow]`:
  `allow_attributes_without_reason` is on.
- A site that needs a banned method of `clippy.toml` says why with
  `#[expect(clippy::disallowed_methods, reason = "...")]`.
