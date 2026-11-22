# checkout-files

The files of a git checkout, as `git ls-files -z --cached --others --exclude-standard -- <roots>` lists them. The
crate reads the index and walks the roots itself, so it needs no `git` executable. The binary
`bins/checkout-files` writes the same answer to stdout.

The answer is the union of two parts:

- The index: each path under the roots, once. A gitlink is left out.
- The walk: each path under the roots that the ignore rules keep. The walk reads `.gitignore` in each directory from
  the checkout to the path, `info/exclude` of the common git directory, and the global exclude file. It does not read
  `.ignore`. It does not enter `.git`, a gitlink of the index, or a directory with a repository in `.git` and no index
  path below it: a nested repository or a submodule. A repository in `.git` is a directory with `HEAD`, or a
  `gitdir:` file whose target holds `HEAD`.

A path is in the answer when it exists and is not a directory. A symbolic link is a path of its own, also when it
points to a directory or to nothing. The paths are relative to the checkout, in slash form, sorted by their bytes and
unique.

## Supported subset

- A checkout whose `.git` is a directory, or a file `gitdir: <path>`, as in a linked work tree. The common directory is
  `commondir` of the git directory, or the git directory itself.
- The index versions 2, 3 and 4. The reader does not verify the checksum, because `index.skipHash` writes zeros there.
  It skips each optional extension, whose signature starts with `A` to `Z`, such as `TREE`, `UNTR` and `EOIE`.
- `core.ignorecase` of the repository config. With it, the patterns match without case, and an untracked path that
  differs from a tracked path only in case is the tracked path.
- The repository config is `config` of the common directory, the files that its `include.path` names, and
  `config.worktree` when `extensions.worktreeConfig` is on. `includeIf` is not read.
- A checkout without an index, as after `git init`, has no tracked path.
- A name that is not UTF-8 outside the roots, because the walk compares the bytes of a name with the roots first.
- A root is a relative slash path. The root `""`, or no root, is the whole checkout. A root inside a nested repository
  or a submodule, and an ignored root without tracked files, give no path, as in git.

## Refusals

| Input | Error |
|---|---|
| a directory without `.git` | `<dir> is not the root of a git checkout` |
| a directory of `checkout_root` or `locate` that does not exist, or with no `.git` above its real path | `cannot resolve the real path of <dir>`, `<dir> is not in a git checkout` |
| a `.git` file without `gitdir:`, or with a git directory that does not exist | `<.git> is a file that does not start with "gitdir:"`, `... which is not a directory` |
| a `gitdir:` file with a relative path or without `commondir`, when `info/exclude` has a pattern | `<exclude> has patterns, but the walk does not read them`. The `ignore` crate reads such a path from the current directory. |
| `core.excludesFile` in the repository config | `<config> sets core.excludesFile, which the listing does not read`. The `ignore` crate reads only the global value. |
| `extensions.objectFormat` other than `sha1` | `<config> sets extensions.objectFormat to "sha256"` |
| a config value of `core.ignorecase` that is not a boolean, and a config file that does not parse | `... which is not a boolean`, `cannot parse the git config <file>` |
| a directory with `.git` and an index path below it. Git enters it, and the `ignore` crate reads it as a repository of its own. | `the directory <dir> holds .git and also tracked paths of the index` |
| a directory with a `.git` that is no repository, such as an empty directory or a file without `gitdir:`. Git walks into it, and the `ignore` crate reads it as a repository of its own. | `the directory <dir> holds a .git that is no repository` |
| a pattern with `{` in a `.gitignore` of a walked directory, in `info/exclude` or in the global exclude file. Git reads the brace as itself, and the `ignore` crate reads `{a,b}` as a choice. | `<file>:<line>: the pattern "<pattern>" holds {` |
| a pattern that the `ignore` crate cannot compile, such as `[z-a]` | `cannot read the ignore files of <dir>` |
| a split index (the extension `link`) | `the index is split` |
| a sparse index (the extension `sdir`) | `the index is sparse` |
| another extension that is not optional | `the index has the required extension "<name>"` |
| an index version other than 2, 3 and 4, a damaged index, and a path that is not UTF-8 | `the index version <n> is not supported`, `the entry <n> ...` |
| a root that is absolute, that has an empty, `.` or `..` segment, or that holds `\`, a leading `:`, `*`, `?` or `[` | `the root "<root>" is not a relative slash path ...` |
| an error of the walk, such as a directory that cannot be read, and a path at or below a root that is not UTF-8 | `cannot walk <checkout>`, `the path <path> is not UTF-8 text` |

## Public items

| Item | Description |
|---|---|
| `list(&Path, &[&str]) -> anyhow::Result<Vec<String>>` | The files of the checkout under the roots. |
| `checkout_root(&Path) -> anyhow::Result<PathBuf>` | The nearest directory from the real path of the given one upwards that holds `.git`, a directory or a file. The real path has no symbolic link and no `.` or `..` segment, and on Windows no `\\?\` prefix. A relative path starts at the current directory. |
| `locate(&Path) -> anyhow::Result<(PathBuf, String)>` | The checkout root of `checkout_root`, and the real path of the given directory relative to it in slash form: the root for `list`. The checkout root itself gives `""`. |
