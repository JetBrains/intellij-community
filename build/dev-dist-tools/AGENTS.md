# dev-dist-tools: agent rules

Read [`README.md`](README.md) first. Read [Rust Code Style](../../.agents/skills/rust-code-style/SKILL.md) for the
idioms: the error model, the command line, the crate rule, the subset rule, the test layout, the lints and the banned
methods. These rules apply to both workspaces, this one and `build/dev-dist-tools` in the ultimate root.

- **Name no implementation language in a spec or in the guide.** Write "the packer", "the composer", "the collector",
  "the launcher", "the descriptor writer". The language belongs in ADR 0020 only.
- **Pass the two gates.** Run `./build/dev-dist.cmd jars` and
  `./bazel.cmd test @community//build/dev-dist-tools/... //build/dev-dist-tools/...` from the ultimate root, then
  `./bazel.cmd test //build/dev-dist-tools/...` in `community/`. The clippy tests of the community crates run only
  there: the clippy aspect skips a target of an external repository.
- **Refresh the Bazel lockfiles after a change of `Cargo.lock`.** Run the two commands of the README, in `community/`
  and in the ultimate root.
- **Pass the Windows gate after a change of file-system code.** Run the two `clippy-windows-*` tests and the
  cross-target `cargo clippy` of the README section "The Windows gate". They lint the `cfg(windows)` code from a macOS
  or Linux host. No test runs on Windows.
- **Keep clippy clean in both workspaces.** `cargo clippy --all-targets` must print no warning in this directory and in
  `build/dev-dist-tools` before a commit. The policy is `community/build/rust-tools/lints.toml`. Change it there, run
  `bun community/build/rust-tools/sync.mjs`, then run it again with `--check`.
