# dist-tools

This Cargo workspace holds the tools that build the split distribution. Each binary under `bins/` is one Bazel
tool. A crate under `crates/` exists when two tools share it, or when it is the frozen-bytes engine of one tool with its
own goldens. Code that one tool uses lives in that tool. Bazel builds every binary through `rules_rs`, and `cargo`
serves the edit-test loop and rust-analyzer.

The ultimate tools `dist` and `content-report` are a second workspace, `build/dist-tools` in the ultimate root.
They use the crates of this workspace through path dependencies.

The contracts are in the specs under `build/openspec/specs/` of the ultimate root. The explainer
[`from-an-iml-to-a-dist.md`](../../../build/docs/explainers/from-an-iml-to-a-dist.md#where-the-code-goes) names each tool by its Bazel label.
[Every dist tool is a Rust binary, and a crate boundary follows a re-key domain](../../../build/openspec/changes/archive/2026-10-01-every-dist-tool-is-a-rust-binary/design.md) records why the tools are Rust.
[Every dist tool is a Rust binary, and a crate boundary follows a re-key domain](../../../build/openspec/changes/archive/2026-10-01-every-dist-tool-is-a-rust-binary/design.md) records the crate rule
and the closure test.
[Every dist tool is a Rust binary, and a crate boundary follows a re-key domain](../../../build/openspec/changes/archive/2026-10-01-every-dist-tool-is-a-rust-binary/design.md) lists the JVM tools that a
distribution still runs. They are not in this workspace.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/appinfo` | The application info: the descriptor XML round trip, the markers, the frontend merge, and the reader of the facts that `product-info.json` states. | `@community//build/dist-tools/crates/appinfo:appinfo_test` |
| `crates/checkout-files` | The files of a git checkout, as `git ls-files --cached --others --exclude-standard` lists them, without the `git` executable: the index reader and the walk. BT links it. Its `Cargo.toml` spells the `[package]` values inline, so a crate of another workspace can link it by path. | `@community//build/dist-tools/crates/checkout-files:checkout-files_test` |
| `crates/cli` | The command line of every tool: the options `--key=value` and `--flag`, the positional arguments, the refusal of every other form, and the `ERROR:` line of a failure. | `@community//build/dist-tools/crates/cli:cli_test` |
| `crates/component` | The component contract of the collector and the composer: the manifest, the types and the reader of the local layout, the core classpath order, and the host paths. | `@community//build/dist-tools/crates/component:component_test` |
| `crates/contentreport` | The reader of an executed packaging recipe and of a built distribution, for `dist` and `content-report`. | `@community//build/dist-tools/crates/contentreport:contentreport_test` |
| `crates/dev-launch` | The `java` command line of a distribution: the distribution properties, the class path, the main class, the custom command, and the text of the argument file. The guest agent of `community/tools/vm` links it, and no Bazel action runs it. It links no other crate of this workspace, and its `Cargo.toml` spells the `[package]` values inline, so a crate of another workspace can link it by path. | `@community//build/dist-tools/crates/dev-launch:dev-launch_test` |
| `crates/distpath` | The slash-path rules: the path inside a distribution, the jar entry name, the link target, and the lexical path functions `clean`, `dir` and `join`. | `@community//build/dist-tools/crates/distpath:distpath_test` |
| `crates/docs-gate` | The docs gate of a docs site on the declared bun and OpenSpec CLI: the declared runtime, the run with a deadline, and the meaning of the sentinel and the exit code. The Rust tests of the docs sites link it. | `@community//build/dist-tools/crates/docs-gate:docs-gate_test` |
| `crates/filemeta` | Inventory JSON version 1, the hash of a link target, and the directory creation with the mode 0755. | `@community//build/dist-tools/crates/filemeta:filemeta_test` |
| `crates/fscopy` | The copy that clones where the volume supports it, the mode helpers, and the path helpers. | `@community//build/dist-tools/crates/fscopy:fscopy_test` |
| `crates/jarpack` | The frozen-bytes engine of the packer: the jar reader and writer, the merge, the `__index__`, and the native tree of a presigned library. The remainder packer merges through it, and the descriptor writer reads library jars through its reader. | `@community//build/dist-tools/crates/jarpack:jarpack_test` |
| `crates/javaglob` | The `java.nio` glob subset that the plan files use. | `@community//build/dist-tools/crates/javaglob:javaglob_test` |
| `crates/planfile` | The plan file, the remainder contract, the plugin classpath record, and the asset and link-graph rules that the remainder packer and the collector share. | `@community//build/dist-tools/crates/planfile:planfile_test` |
| `crates/pluginpack` | The remainder engine: the plan and the execution of a plugin remainder, with the Kotlin goldens. Only the remainder packer links it. | `@community//build/dist-tools/crates/pluginpack:pluginpack_test` |
| `crates/testkit` | The helpers that the tests of several crates share: the `testdata/` lookup, a temporary directory with a real path, the working directory of one test, and the file, mode and link helpers. Only `[dev-dependencies]` name it, so no tool links it. | `@community//build/dist-tools/crates/testkit:testkit_test` |
| `crates/trace` | The span API of the traced tools and the Jaeger span file of `--trace-file`. The API is written by hand, so no tool links the `tracing` crates. | `@community//build/dist-tools/crates/trace:trace_test` |
| `crates/xxh3` | The hash4j xxh3 hashes: the two hashes of the `__index__` keys, and the content hash of a file or a stream in blocks of 256 KiB. | `@community//build/dist-tools/crates/xxh3:xxh3_test` |
| `bins/checkout-files` | The command line of `crates/checkout-files`: it writes the files under the roots to stdout, each followed by a NUL byte. A test that reads the checkout runs it. | `@community//build/dist-tools/bins/checkout-files:checkout-files_test` and `:checkout-files_cli_test` |
| `bins/content-module-packer` | The packer and the inventory of each packed jar. | `@community//build/dist-tools/bins/content-module-packer:content-module-packer_test` |
| `bins/dist-collector` | The collector: the inventory of a component and the plugin classpath record. | `@community//build/dist-tools/bins/dist-collector:dist-collector_test` |
| `bins/dev-home` | The generation of a launch target: the home of real files outside the output base, the content-addressed store, and the garbage collection. The stub of a launch target and the IDE run handlers run it before the native launcher. | `@community//build/dist-tools/bins/dev-home:dev-home_test` |
| `bins/dist-composer` | The composer: the composition spec, the composition and its copy step, and the local layout writer. It also writes the plugin classpath file and the fingerprint. It checks the home placement, and it writes the executable list of a context plugin pool. | `@community//build/dist-tools/bins/dist-composer:dist-composer_test` |
| `bins/plugin-descriptor-writer` | The descriptor writer. | `@community//build/dist-tools/bins/plugin-descriptor-writer:plugin-descriptor-writer_test` and `:descriptor_rule_tests` |
| `bins/plugin-remainder-packer` | The remainder packer. | `@community//build/dist-tools/bins/plugin-remainder-packer:plugin-remainder-packer_test` and `:plugin-remainder-packer_cli_test` |
| `bins/product-files` | The tool of `dist_product_files`. | `@community//build/dist-tools/bins/product-files:product-files_test` |
| `bins/project-model-tree` | The `materializer` of `intellij_project_model_tree`. | `@community//build/dist-tools/bins/project-model-tree:project-model-tree_test` |
| `bins/runtime-layout` | The runtime layout tool. | `@community//build/dist-tools/bins/runtime-layout:runtime-layout_test` |
| `bins/tool-identity` | The identity writer: the identity file of each distribution tool, from the facts and the files that `dist_tool` states. | `@community//build/dist-tools/bins/tool-identity:tool-identity_test` |
| `build/dist-tools/bins/dist` | The binary of `./build/dist.cmd`, in the ultimate root. | `//build/dist-tools/bins/dist:dist_test` |
| `build/dist-tools/bins/content-report` | The `content-report pure` command, in the ultimate root. | `//build/dist-tools/bins/content-report:content-report_test` |

The `API.md` of a crate states its supported subset and the input that it refuses. Rustdoc states the public items.

## The two gates

A change to a tool must pass both gates. Run them from the ultimate root, then the third command in `community/`.

```sh
./build/dist.cmd jars
./bazel.cmd test @community//build/dist-tools/... //build/dist-tools/...
cd community && ./bazel.cmd test //build/dist-tools/...
```

- `jars` pairs each packed content-module jar of the platform payload with its `JarPackager` reference jar and compares the
  bytes. It fails on a differing jar, on a jar without a partner on either side, and on an empty compare.
- The tests include the frozen jar digests in `crates/jarpack/src/tests/golden.rs`, the Kotlin goldens of
  `crates/pluginpack/testdata`, the plan file corpus, the crate closure test of each action tool, and the
  `<crate>-clippy` test of every crate.
- The clippy tests of the community crates run only from `community/`. The clippy aspect of rules_rust skips a target
  of an external repository, and from the ultimate root this module is one. There the tests are incompatible, and
  `bazel test` reports them as skipped. The clippy tests of the ultimate tools run from the ultimate root.
- The fingerprint of `//build:idea_air_dist` and `./build/dist.cmd snapshot diff` guard a change of the composed
  bytes. The validation spec states them. Run them after a change that can move a composed byte:

```sh
./bazel.cmd build //build:idea_air_dist && cat out/bazel-bin/build/idea_air_dist.dist/fingerprint.txt
./build/dist.cmd snapshot take //build:idea_air_dist out/dist-snapshots/<name>.json
./build/dist.cmd snapshot diff out/dist-snapshots/<baseline>.json out/dist-snapshots/<name>.json
```

- A change of a tool that a rule test runs, such as the descriptor writer or the remainder packer, also passes
  `./bazel.cmd test @community//platform/build-scripts/bazel-rules:bazel_rules_tests`.
- Before a commit, run `cargo fmt --check` and `cargo clippy --all-targets` in this directory and in
  `build/dist-tools`, and `bun community/build/rust-tools/sync.mjs --check` from the ultimate root.
- `./build/dist.cmd` runs `//build/dist-tools/bins/dist:dist_opt`, the binary built in `opt`, and
  `replay` runs `bins/content-module-packer:content-module-packer_opt`. The unit test of each binary stays on the
  `rust_binary`.
- A launch target runs one tool of this workspace at start: `bins/dev-home:dev-home_opt`, the generation writer. Its
  stub runs it, and then the native launcher of the generation.

### The Windows gate

The CI only builds on Windows, so the Windows check runs from a macOS or Linux host. Run it after a change of
file-system code or of a `cfg(windows)` branch:

```sh
cd community && ./bazel.cmd test //build/dist-tools:clippy-windows-x86_64 //build/dist-tools:clippy-windows-arm64
RUSTC_BOOTSTRAP=1 cargo clippy -Zbuild-std=std,panic_abort --target x86_64-pc-windows-msvc --workspace --all-targets
```

- The two Bazel tests lint every binary of this workspace and every crate that it links, for each Windows platform.
  `./bazel.cmd test //build/dist-tools/...` in `community/` runs them too.
- Run the cargo command in this directory and in `build/dist-tools`. It also lints the tests and the ultimate
  tools, which the Bazel tests do not.
- Both only check the code. They run no test on Windows.
- A test run on a Windows host stays possible, for a change that the path length can break. Run `cargo test
  --workspace` as a normal user, with Developer Mode on and `TMP` and `TEMP` at a directory of about 230 characters.
  Then every test path is longer than `MAX_PATH`. The known failures come from Unix modes, `/` separators, Unix-only
  tools, and link targets longer than `MAX_PATH` in the test fixtures.

### The crate closure test

Each action tool under `bins/` has the test `<bin>_closure_test`. It compares the crates that the tool links with
`closure.txt` of its package. A change to a crate changes the bytes of each tool that links it. Then Bazel reruns every
action of that tool, and the packer runs once per content-module jar. The test makes each new crate in a closure a
visible choice.

`closure.txt` lists the rustc crate names, one per line. It leaves out the proc macros, because the compiler runs them
and the tool does not link them. It also leaves out the host-only crates of `_HOST_CRATES` in `community/build/rust-tools/defs.bzl`, so one file
holds on macOS, Linux and Windows. A new dependency that only some hosts link adds its crates to `_HOST_CRATES`.
`cargo tree --target <triple>` shows the crates of each host.

After an intended change of a closure, regenerate the file in `community/`:

```sh
./bazel.cmd build //build/dist-tools/bins/<bin>:<bin>_closure
cp out/bazel-bin/build/dist-tools/bins/<bin>/<bin>_closure.txt build/dist-tools/bins/<bin>/closure.txt
```

## The subset rule

A tool supports only the input that the repository produces. It refuses all other input with an error that names the
input. So an unused shape of the former tool has no code here, and a shape that no producer writes cannot pass in
silence.

`crates/planfile/testdata/corpus` holds a copy of the checked-in plan files. The corpus test reads and derives every
copy, and it requires every accepted source kind and operation kind to occur. The `javaglob` test requires a recorded
JDK answer for every glob of the corpus. A plan author who needs a new shape changes the contract, the corpus and the
`API.md` of the crate together.

## Cargo beside Bazel

- Run `cargo test`, `cargo clippy` and `cargo fmt` in this directory, or in `build/dist-tools` for the ultimate
  tools. Bazel does not read `Cargo.toml` of the ultimate workspace. Cargo finds `.cargo/config.toml` from the
  current directory: `--manifest-path` from another directory builds into `target/` of the workspace.
- `.cargo/config.toml` of both workspaces puts the cargo output in `community/out/cargo-target/dist-tools`.
  Git and Bazel ignore that directory.
- Two cargo runs on one target directory wait for its lock. Set `CARGO_TARGET_DIR` to another directory under `out/`
  for a second run at the same time.
- `[workspace.lints]` of `Cargo.toml`, `rustfmt.toml` and `clippy.toml` of both workspaces are copies of the
  sources in [`community/build/rust-tools`](../rust-tools/README.md), the shared configuration of the Rust tool
  workspaces. Edit a source there, then run `bun community/build/rust-tools/sync.mjs`.
- `rustfmt.toml` sets the width to 140 columns. Format with `cargo fmt` before a commit.
- Bazel applies the lint table through `@dt` and `:lints`, and the `<crate>-clippy` test of each crate fails on any
  clippy warning. `cargo clippy --all-targets` shows the same findings in the edit loop. A site-local exception is
  `#[expect(lint, reason = "...")]`.
- A module `<module>.rs` ends with `#[cfg(test)] mod tests;`, and its tests are in `<module>/tests.rs`. The tests of
  `lib.rs` or `main.rs` are in `src/tests.rs`. The tests that run a binary as a process are in `tests/cli.rs`.
- A fixture of one crate, such as the jar writers and the frozen digests of `jarpack`, is a submodule of `src/tests.rs`
  in `src/tests/<fixture>.rs`. A `src/test_support.rs` holds the fakes of the types of one crate, such as its
  manifests, its requests or its source bindings. The helpers that the tests of several crates share are in
  `crates/testkit`.
- A test reads `testdata/` through `testkit::testdata_dir`: from `DT_TESTDATA_DIR` under Bazel, and from the run-time
  `CARGO_MANIFEST_DIR` under `cargo test`. The process wrapper of rules_rust refuses `env!("CARGO_MANIFEST_DIR")`.
- A test that reads the `testdata/` of another crate adds that crate's `<name>_testdata` filegroup to `test_data` of
  `dist_rust_crate`. The `javaglob` crate reads the plan corpus this way.
- A new crate or binary goes into `members` of `Cargo.toml`. Then run `cargo build` to update `Cargo.lock`.

### After a change of `Cargo.lock`

`community/MODULE.bazel` generates the crate hub `@dt` from `Cargo.toml`, `Cargo.lock` and `.cargo/config.toml` of
this directory. The hub also resolves the triples `aarch64-unknown-linux-musl` and `x86_64-unknown-linux-musl`, where
no distribution tool runs. The guest agent of `community/tools/vm` links `dev-launch` for both, and the closure checks of
`vm` and `air-trace` analyze `distpath` for x86_64. After a change of `Cargo.lock`, update the Bazel
lockfiles of both roots:

```sh
cd community && ./bazel.cmd build --nobuild --lockfile_mode=update //build/dist-tools/...
cd .. && ./bazel.cmd build --nobuild --lockfile_mode=update @community//build/dist-tools/... //build/dist-tools/...
```

The extension watches only the workspace `Cargo.toml` and `Cargo.lock`. A dependency that moves between
`[dev-dependencies]` and `[dependencies]` inside a member manifest leaves `Cargo.lock` unchanged, so `@dt` keeps the
old split until the workspace `Cargo.toml` changes. Touch it (a comment line is enough) and run the two commands again.

### The ultimate workspace

- Bazel builds the ultimate binaries with the crates of `@dt`, so each crate has one copy.
- List a dependency in `Cargo.toml` of the binary and in `deps` of its `BUILD.bazel`, and a dev-dependency in
  `test_deps`.
- `Cargo.toml` of the ultimate workspace carries the same `[workspace.lints]` copy for `cargo clippy`. Bazel reads the
  community copy: `dist_rust_binary` passes `@community//build/dist-tools:lints` to every target.
- Name a community crate by its label, such as `@community//build/dist-tools/crates/jarpack`.
- Name a third-party crate with `dt_crate()` of `@community//build/dist-tools:defs.bzl`. A community crate must
  depend on it with the same version requirement, or `@dt` does not have it.

## Profile the packer

The packer accepts many `output=` groups in one flag file, so one process can pack the whole population. This command
gives every recipe as its expanded command line:

```sh
./bazel.cmd aquery --output=text --include_param_files 'mnemonic("PackContentModuleJar", //... + @community//...)'
```

Join the recipes into one flag file, and change each `output=` path so that the run does not write into `bazel-out`.
Remove the groups whose sources are not in the output base. Then run the packer from the exec root under a sampling
profiler. The measurement rules are in `build/AGENTS.md` of the ultimate root.
