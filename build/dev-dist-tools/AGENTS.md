# dev-dist-tools: agent rules

Read [`README.md`](README.md) first. These rules apply to both workspaces, this one and `build/dev-dist-tools` in the
ultimate root.

- **Support the subset, refuse the rest.** A tool accepts only the input that the repository produces. It refuses all
  other input with an error that names the input. List each refusal in the `API.md` of the crate.
- **Prefer a maintained crate to own code.** Write code by hand only where a crate cannot give the frozen bytes or the
  required behavior. State the reason in one sentence in the doc comment of that code.
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
- **Do not call a banned method.** `clippy.toml` bans `fs::canonicalize` and the tempfile calls that fail past
  `MAX_PATH`, and names the replacement of each. Its source is `community/build/rust-tools/clippy.toml`.
- **Follow the lint policy.** `cargo clippy --all-targets` must print no warning in both workspaces before a commit.
  The policy is `community/build/rust-tools/lints.toml`; change it there, run `bun community/build/rust-tools/sync.mjs`,
  then run it again with `--check`. A site-local exception is `#[expect(lint, reason = "...")]`.
