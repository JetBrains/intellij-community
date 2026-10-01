---
name: rust-code-style
description: Write or review Rust in the build tools: the error model, the CLI grammar, the crate rule, the test layout, the shared lint, format and Bazel configuration.
---

# Rust Code Style

These rules apply to the Rust tool workspaces: the dev-dist tools, BT and the Air UI-lane tooling. The spec of a
workspace is [`community/build/rust-tools/README.md`](../../../build/rust-tools/README.md). It names the files, the
Bazel targets and the checks that every workspace has. This skill holds the idioms.

## First, find the audience

A tool has one of two audiences. The audience picks the error model and the command line grammar.

| | Action tool | Person- or agent-facing CLI |
|---|---|---|
| Writes the command line | Bazel, in an action | a person, an agent or a script |
| Examples | the packer, the collector, the composer, the descriptor writer | `bt`, `vm` |
| Error model | `anyhow`, `ERROR: {e:#}`, a nonzero exit | a typed `Refusal` with a stable code and exit |
| Command line | the `cli` crate, `--key=value` | clap derive |
| Crate closure | pinned in `closure.txt` | not pinned |

Bazel reads only "nonzero" from an action tool. A person or an agent branches on the exit code and on the refusal code.

## 1. Two error models

An action tool returns `anyhow::Result` from every function and prints the chain once. `main.rs` stays thin:

```rust
fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}
```

`run` calls the tool and passes an error to `cli::report`, which prints `ERROR: {error:#}`. Each tool keeps its own exit
codes. `bins/runtime-layout/src/main.rs` of the dev-dist tools shows the shape.

A person- or agent-facing CLI answers a `Refusal { code, message, exit, details }`. The code is the stable half that a
caller automates against. The shared crate `community/tools/bt/crates/refusal` holds the type, with the details as JSON
text. Its type parameter is the exit vocabulary, so each tool keeps its own exit codes: `bt` uses `u8` with the codes 0
to 6 of `community/tools/bt/crates/bt-core/src/exit.rs`, and the Air UI-lane controller uses `avl_base::Exit`. A tool
that converts a refusal of another tool maps the exit by its meaning, never by its number.

## 2. Two command line grammars

Bazel writes the argv of an action tool, so the tool reads it with the `cli` crate. The crate has no dependency, and
it refuses every other form with one text. `community/build/dev-dist-tools/crates/cli/API.md` lists the refusals.

```rust
let mut options = cli::parse(args)?;
let output = options.require("--output")?;
let parts = options.take_all("--part")?;
options.finish()?; // refuses an option that the tool did not take
```

A person- or agent-facing CLI uses clap derive: help, environment variables, suggestions and `--` passthrough.
`community/tools/bt/bins/bt/src/options.rs` is the example. Use no third grammar and no hand-written argument loop.

## 3. Format

- The width is 140 columns, as in the root `.editorconfig`. The synced `rustfmt.toml` of each workspace sets it.
- Run `cargo fmt` in the workspace directory before every commit.
- Format a whole workspace in its own commit, without other changes.

## 4. Lints

- Every crate follows the shared table: `[lints] workspace = true` in its `Cargo.toml`. The crate macro fails
  without it.
- A site exception is `#[expect(lint, reason = "...")]`, never `#[allow]`. An expectation that stops firing fails
  the build, so a stale exception shows.
- `clippy.toml` bans `fs::canonicalize` and the tempfile calls that fail past `MAX_PATH` on Windows. A site that
  needs a banned call states why:

```rust
#[expect(
    clippy::disallowed_methods,
    reason = "the parent resolves the expected directory with the same call, so both sides agree on /private on macOS"
)]
```

## 5. Errors in libraries

- Use `anyhow` in a library too. Use `io::Result` only where a caller branches on the `ErrorKind`, as `fscopy` does.
- A refusal names the refused input: the file, the line, the field or the value.
- Add the path as context at the I/O call: `.with_context(|| format!("cannot read {}", path.display()))`.
- A test pins the whole chain with `format!("{error:#}")`. A test that checks a kind uses
  `error.downcast_ref::<io::Error>()`.

## 6. Types

- Use an enum where a Go port used flags or a struct of options. A match then covers every case.
- Use `Path` and `PathBuf` for every host path. Use `str` only for a slash-form path inside a distribution or a jar.
  `distpath` validates such a path once, at the input boundary.
- Write `pub(crate)` inside a binary. The `unreachable_pub` lint is on.
- Keep `main.rs` thin: parse, call, report. The work lives in modules that the tests call directly.

## 7. Crates

- A crate exists when two binaries share it, or when it is the frozen-bytes engine of one binary with its own
  goldens. Code that one binary uses lives in that binary.
- An action tool pins its crate closure in `closure.txt`, such as
  `community/build/dev-dist-tools/bins/content-module-packer/closure.txt`. A change to any crate in the closure reruns
  every action of the tool, so `<bin>_closure_test` makes each new crate a visible choice.
- Prefer a maintained crate to own code. The exception is a crate that would re-key an action tool, when about 30
  lines replace it. State the reason in one sentence of the doc comment:

```rust
/// It is written by hand, because a crate dependency in the packer re-keys every packing action when the crate changes.
pub(crate) fn is_versioned_module_info(name: &str) -> bool {
```

- Support the subset: a tool accepts only the input that the repository produces. It refuses all other input with an
  error that names the input. The `API.md` of the crate lists each refusal.

## 8. Tests

- `<module>.rs` ends with `#[cfg(test)] mod tests;`, and the tests live in `<module>/tests.rs`. The tests of
  `lib.rs` or `main.rs` live in `src/tests.rs`. There is no `mod.rs`: `mod_module_files` is on.
- The process-level tests of a binary live in `tests/cli.rs`. The run-time `CARGO_BIN_EXE_<name>` gives the binary.
  Under Bazel the path is relative, so make it absolute before the test changes the directory.
- Shared test helpers live in a dev-only testkit crate. Shipped code never depends on it.
- A test finds `testdata/` through one lookup: the variable that the crate macro sets under Bazel, such as
  `DDT_TESTDATA_DIR`, and the run-time `CARGO_MANIFEST_DIR` under cargo. The rules_rust process wrapper refuses
  `env!("CARGO_MANIFEST_DIR")`. A file that `include_str!` reads is `compile_data` of the crate.
- Never edit a golden by hand. Regenerate it with the update path of its test.
- The Windows check is the `clippy-windows-*` targets of the workspace and the cross-target cargo clippy below.

## 9. Docs

- The `README.md` of a workspace holds the crate map and the gates.
- The `API.md` of a crate under the subset rule holds the supported subset and a refusal table.
- A doc comment documents a declaration. The rationale of a change goes in the commit message or an ADR, not in a
  comment.

## 10. Versions

- A requirement names the current full version, such as `serde = "1.0.229"`. Cargo reads it as a caret requirement.
- `Cargo.lock` is the pin, and rules_rs reads it.
- To stay current, run `cargo update`, raise the requirements to the locked versions, and refresh the hub.

## Refresh a crate hub

`community/MODULE.bazel` generates `@ddt` and `@bt` from the workspace `Cargo.toml` and `Cargo.lock`. After a change of
`Cargo.lock`, update the Bazel lockfiles of both roots. For the dev-dist tools:

```sh
cd community && ./bazel.cmd build --nobuild --lockfile_mode=update //build/dev-dist-tools/...
cd .. && ./bazel.cmd build --nobuild --lockfile_mode=update @community//build/dev-dist-tools/... //build/dev-dist-tools/...
```

For BT, use `//tools/bt/...` and `@community//tools/bt/...`. The extension watches only the workspace `Cargo.toml` and
`Cargo.lock`. A dependency that moves between `[dev-dependencies]` and `[dependencies]` of a member leaves the lock
unchanged. Then touch the workspace `Cargo.toml`, for example with a comment line, and run the commands again.

## The gates

Run them before a commit, in addition to the gates of the workspace README:

```sh
cargo fmt --check && cargo clippy --all-targets      # in the workspace directory
bun community/build/rust-tools/sync.mjs --check
cd community && ./bazel.cmd test //build/dev-dist-tools/... //tools/bt/... //build/rust-tools/...
RUSTC_BOOTSTRAP=1 cargo clippy -Zbuild-std=std,panic_abort --target x86_64-pc-windows-msvc --workspace --all-targets
```

From the ultimate root, also run `./bazel.cmd test @community//build/dev-dist-tools/... //build/dev-dist-tools/...`.
The clippy tests of a community crate run only from `community/`, because the clippy aspect skips a target of an
external repository.

## Where things live

| What | Where |
|---|---|
| The spec of a workspace, the checks, how a workspace joins | [`community/build/rust-tools/README.md`](../../../build/rust-tools/README.md) |
| The lint, format and clippy sources, and `sync.mjs` | `community/build/rust-tools/` |
| The Bazel macro core: `rust_tool_crate`, `optimized_binary`, `windows_clippy_tests` | `community/build/rust-tools/defs.bzl` |
| The dev-dist tools: crate map, gates, agent rules | [`community/build/dev-dist-tools/README.md`](../../../build/dev-dist-tools/README.md) and `AGENTS.md` |
| BT | [`community/tools/bt/README.md`](../../../tools/bt/README.md) |
| The Air UI-lane tooling, which joins the spec at its rework | `plugins/air/tests/integration/vm-lane/README.md` |
