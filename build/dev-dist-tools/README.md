# dev-dist-tools

This Cargo workspace holds the tools that build the split dev distribution. Each binary under `bins/` is one Bazel
tool. Each crate under `crates/` holds one concern that several tools share. Bazel builds every binary through
`rules_rs`, and `cargo` serves the edit-test loop and rust-analyzer.

The ultimate tools `dev-dist` and `content-report` are a second workspace, `build/dev-dist-tools` in the ultimate root.
They use the crates of this workspace through path dependencies.

The contracts are in the specs under `build/spec/` of the ultimate root. The guide
[`dev-build-architecture.md`](../../../build/dev-build-architecture.md) names each tool by its Bazel label.
[ADR 0020](../../../build/decisions/0020-the-dev-dist-tools-are-rust.md) records why the tools are Rust.
[ADR 0027](../../../build/decisions/0027-four-jvm-tools-stay-on-the-distribution-path.md) lists the JVM tools that a
distribution still runs. They are not in this workspace.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/appinfo` | The application info: the descriptor XML round trip, the markers, the frontend merge, and the reader of the facts that `product-info.json` states. | `@community//build/dev-dist-tools/crates/appinfo:appinfo_test` |
| `crates/cli` | The command line of every tool: the options `--key=value` and `--flag`, the positional arguments, the refusal of every other form, and the `ERROR:` line of a failure. | `@community//build/dev-dist-tools/crates/cli:cli_test` |
| `crates/component` | The component contract of the collector, the composer and the launcher: the manifest, the types and the reader of the local layout, the core classpath order, and the host paths. | `@community//build/dev-dist-tools/crates/component:component_test` |
| `crates/contentreport` | The reader of an executed packaging recipe and of a built distribution, for `dev-dist` and `content-report`. | `@community//build/dev-dist-tools/crates/contentreport:contentreport_test` |
| `crates/distpath` | The slash-path rules: the path inside a distribution, the jar entry name, the link target, and the Go `path` functions. | `@community//build/dev-dist-tools/crates/distpath:distpath_test` |
| `crates/filemeta` | Inventory JSON version 1, the hash of a link target, and the directory creation with the mode 0755. | `@community//build/dev-dist-tools/crates/filemeta:filemeta_test` |
| `crates/fscopy` | The copy that clones where the volume supports it, the mode helpers, and the path helpers. | `@community//build/dev-dist-tools/crates/fscopy:fscopy_test` |
| `crates/jarpack` | The packer core: the jar merge, the `__index__`, and the native tree of a presigned library. | `@community//build/dev-dist-tools/crates/jarpack:jarpack_test` |
| `crates/javaglob` | The `java.nio` glob subset that the plan files use. | `@community//build/dev-dist-tools/crates/javaglob:javaglob_test` |
| `crates/planfile` | The plan file, the remainder contract, the plugin classpath record, and the asset and link-graph rules that the remainder packer and the collector share. | `@community//build/dev-dist-tools/crates/planfile:planfile_test` |
| `crates/pluginpack` | The plan and the execution of a plugin remainder, for the remainder packer. | `@community//build/dev-dist-tools/crates/pluginpack:pluginpack_test` |
| `crates/trace` | The span API of the traced tools and the Jaeger span file of `--trace-file`. | `@community//build/dev-dist-tools/crates/trace:trace_test` |
| `crates/xxh3` | The hash4j xxh3 hashes: the two hashes of the `__index__` keys, and the content hash of a file or a stream in blocks of 256 KiB. | `@community//build/dev-dist-tools/crates/xxh3:xxh3_test` |
| `bins/content-module-packer` | The packer and the inventory of each packed jar. | `@community//build/dev-dist-tools/bins/content-module-packer:content-module-packer_test` |
| `bins/dev-dist-collector` | The collector: the inventory of a component and the plugin classpath record. | `@community//build/dev-dist-tools/bins/dev-dist-collector:dev-dist-collector_test` |
| `bins/dev-dist-composer` | The composer: the composition spec, the composition and its copy step, the local layout writer, the plugin classpath file and the fingerprint. | `@community//build/dev-dist-tools/bins/dev-dist-composer:dev-dist-composer_test` |
| `bins/dev-launcher` | The launcher of `intellij_dev_launcher`, the local home, and the `local-home` command of `PreBuiltDevMain`. | `@community//build/dev-dist-tools/bins/dev-launcher:dev-launcher_test` |
| `bins/plugin-descriptor-writer` | The descriptor writer. | `@community//build/dev-dist-tools/bins/plugin-descriptor-writer:plugin-descriptor-writer_test` and `:descriptor_rule_tests` |
| `bins/plugin-remainder-packer` | The remainder packer. | `@community//build/dev-dist-tools/bins/plugin-remainder-packer:plugin-remainder-packer_test` and `:plugin-remainder-packer_packer_test` |
| `bins/product-files` | The tool of `dev_dist_product_files`. | `@community//build/dev-dist-tools/bins/product-files:product-files_test` |
| `bins/project-model-tree` | The `materializer` of `intellij_project_model_tree`. | `@community//build/dev-dist-tools/bins/project-model-tree:project-model-tree_test` |
| `bins/runtime-layout` | The runtime layout tool. | `@community//build/dev-dist-tools/bins/runtime-layout:runtime-layout_test` |
| `build/dev-dist-tools/bins/dev-dist` | The binary of `./build/dev-dist.cmd`, in the ultimate root. | `//build/dev-dist-tools/bins/dev-dist:dev-dist_test` |
| `build/dev-dist-tools/bins/content-report` | The `content-report pure` command, in the ultimate root. | `//build/dev-dist-tools/bins/content-report:content-report_test` |

The `API.md` of each crate lists its public items and the input that it refuses.

## The two gates

A change to a tool must pass both gates. Run them from the ultimate root, then the third command in `community/`.

```sh
./build/dev-dist.cmd jars
./bazel.cmd test @community//build/dev-dist-tools/... //build/dev-dist-tools/...
cd community && ./bazel.cmd test //build/dev-dist-tools/...
```

- `jars` compares every packed content-module jar with the `JarPackager` reference, byte for byte.
- The tests include the frozen jar digests in `crates/jarpack/src/tests/golden.rs`, the Kotlin goldens of
  `crates/pluginpack/testdata`, the plan file corpus, the crate closure test of each action tool, and the
  `<crate>-clippy` test of every crate.
- The clippy tests of the community crates run only from `community/`. The clippy aspect of rules_rust skips a target
  of an external repository, and from the ultimate root this module is one. There the tests are incompatible, and
  `bazel test` reports them as skipped. The clippy tests of the ultimate tools run from the ultimate root.
- The fingerprint of `//build:idea_air_dist` and `./build/dev-dist.cmd snapshot diff` guard a change of the composed
  bytes. The validation spec states them.
- `./build/dev-dist.cmd` runs `//build/dev-dist-tools/bins/dev-dist:dev-dist_opt`, the binary built in `opt`. The
  unit test of `dev-dist` stays on the `rust_binary`.

### The Windows gate

The CI only builds on Windows, so the Windows check runs from a macOS or Linux host. Run it after a change of
file-system code or of a `cfg(windows)` branch:

```sh
cd community && ./bazel.cmd test //build/dev-dist-tools:clippy-windows-x86_64 //build/dev-dist-tools:clippy-windows-arm64
RUSTC_BOOTSTRAP=1 cargo clippy -Zbuild-std=std,panic_abort --target x86_64-pc-windows-msvc --workspace --all-targets
```

- The two Bazel tests lint every binary of this workspace and every crate that it links, for each Windows platform.
  `./bazel.cmd test //build/dev-dist-tools/...` in `community/` runs them too.
- Run the cargo command in this directory and in `build/dev-dist-tools`. It also lints the tests and the ultimate
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
visible choice. The launcher runs no Bazel action, so it has no closure test.

`closure.txt` lists the rustc crate names, one per line. It leaves out the proc macros, because the compiler runs them
and the tool does not link them. It also leaves out the host-only crates of `_HOST_CRATES` in `community/build/rust-tools/defs.bzl`, so one file
holds on macOS, Linux and Windows. A new dependency that only some hosts link adds its crates to `_HOST_CRATES`.
`cargo tree --target <triple>` shows the crates of each host.

After an intended change of a closure, regenerate the file in `community/`:

```sh
./bazel.cmd build //build/dev-dist-tools/bins/<bin>:<bin>_closure
cp out/bazel-bin/build/dev-dist-tools/bins/<bin>/<bin>_closure.txt build/dev-dist-tools/bins/<bin>/closure.txt
```

## The subset rule

A tool supports only the input that the repository produces. It refuses all other input with an error that names the
input. So an unused shape of the Go original has no code here, and a shape that no producer writes cannot pass in
silence.

`crates/planfile/testdata/corpus` holds a copy of the checked-in plan files. The corpus test reads and derives every
copy, and it requires every accepted source kind and operation kind to occur. The `javaglob` test requires a recorded
JDK answer for every glob of the corpus. A plan author who needs a new shape changes the contract, the corpus and the
`API.md` of the crate together.

## Cargo beside Bazel

- Run `cargo test`, `cargo clippy` and `cargo fmt` in this directory, or in `build/dev-dist-tools` for the ultimate
  tools. Bazel does not read `Cargo.toml` of the ultimate workspace.
- `.cargo/config.toml` of both workspaces puts the cargo output in `community/out/cargo-target/dev-dist-tools`.
  Git and Bazel ignore that directory.
- Two cargo runs on one target directory wait for its lock. Set `CARGO_TARGET_DIR` to another directory under `out/`
  for a second run at the same time.
- `[workspace.lints]` of `Cargo.toml`, `rustfmt.toml` and `clippy.toml` of both workspaces are copies of the
  sources in [`community/build/rust-tools`](../rust-tools/README.md), the shared configuration of the Rust tool
  workspaces. Edit a source there, then run `bun community/build/rust-tools/sync.mjs`.
- `rustfmt.toml` sets the width to 140 columns. Format with `cargo fmt` before a commit.
- Bazel applies the lint table through `@ddt` and `:lints`, and the `<crate>-clippy` test of each crate fails on any
  clippy warning. `cargo clippy --all-targets` shows the same findings in the edit loop. A site-local exception is
  `#[expect(lint, reason = "...")]`.
- A test reads `testdata/` from `DDT_TESTDATA_DIR` under Bazel, and from the run-time `CARGO_MANIFEST_DIR` under
  `cargo test`. The process wrapper of rules_rust refuses `env!("CARGO_MANIFEST_DIR")`.
- A test that reads the `testdata/` of another crate adds that crate's `<name>_testdata` filegroup to `test_data` of
  `dev_dist_rust_crate`. The `javaglob` crate reads the plan corpus this way.
- A new crate or binary goes into `members` of `Cargo.toml`. Then run `cargo build` to update `Cargo.lock`.

### After a change of `Cargo.lock`

`community/MODULE.bazel` generates the crate hub `@ddt` from `Cargo.toml`, `Cargo.lock` and `.cargo/config.toml` of
this directory. After a change of `Cargo.lock`, update the Bazel lockfiles of both roots:

```sh
cd community && ./bazel.cmd build --nobuild --lockfile_mode=update //build/dev-dist-tools/...
cd .. && ./bazel.cmd build --nobuild --lockfile_mode=update @community//build/dev-dist-tools/... //build/dev-dist-tools/...
```

The extension watches only the workspace `Cargo.toml` and `Cargo.lock`. A dependency that moves between
`[dev-dependencies]` and `[dependencies]` inside a member manifest leaves `Cargo.lock` unchanged, so `@ddt` keeps the
old split until the workspace `Cargo.toml` changes. Touch it (a comment line is enough) and run the two commands again.

### The ultimate workspace

- Bazel builds the ultimate binaries with the crates of `@ddt`, so each crate has one copy.
- List a dependency in `Cargo.toml` of the binary and in `deps` of its `BUILD.bazel`.
- `Cargo.toml` of the ultimate workspace carries the same `[workspace.lints]` copy for `cargo clippy`. Bazel reads the
  community copy: `dev_dist_rust_binary` passes `@community//build/dev-dist-tools:lints` to every target.
- Name a community crate by its label, such as `@community//build/dev-dist-tools/crates/jarpack`.
- Name a third-party crate with `ddt_crate()` of `@community//build/dev-dist-tools:defs.bzl`. A community crate must
  depend on it with the same version requirement, or `@ddt` does not have it.

## Profile the packer

The packer accepts many `output=` groups in one flag file, so one process can pack the whole population. This command
gives every recipe as its expanded command line:

```sh
./bazel.cmd aquery --output=text --include_param_files 'mnemonic("PackContentModuleJar", //... + @community//...)'
```

Join the recipes into one flag file, and change each `output=` path so that the run does not write into `bazel-out`.
Remove the groups whose sources are not in the output base. Then run the packer from the exec root under a sampling
profiler. The measurement rules are in `build/AGENTS.md` of the ultimate root.
