# filemeta

The file metadata of the dev-distribution tools: inventory JSON version 1. Port of the Go package
`internal/filemetadata`. The path and link rules of `internal/filemetadata` are in `distpath`. The content hash of a
file, `HashFile`, is `xxh3::hash_file` of the `xxh3` crate.

## File format

`{"version":1,"entries":[...]}` and one `\n`, with no indentation. The entries are sorted bytewise by `relativePath`.
An entry holds `relativePath, type, hash, size, mode, executable, symlinkTarget` in this order. A directory has no
`hash`. `symlinkTarget` is present only when it is not empty. `serde_json` writes the document.

## Supported subset

The crate checks each path with `distpath::validate_path`, each link target with `distpath::validate_link_target`,
and the links of a set of entries with `distpath::validate_links`. So it refuses the paths and links that the `API.md`
of `distpath` lists, with the same text: a name outside ASCII or with `<`, `>` or `&`, a link chain, and a link target
with an empty segment.

## Errors

Every function that can fail returns `anyhow::Result`, except the two `io::Result` functions below. A file system
failure has the path as its context, so `{:#}` prints `<path>: <error>` and `downcast_ref::<io::Error>()` gives the
`io::ErrorKind`. A refusal is one message with the text of the Go original, or a text that names the unsupported input.
`read` puts the path of the file before a refusal of its content.

## Public items

| Item | Go original | Description |
|---|---|---|
| `enum EntryType { File, Directory, Symlink }` | `Entry.Type` | The JSON `type`: `file`, `directory` or `symlink`. |
| `struct Entry` | `Entry` | `relative_path`, `entry_type`, `hash`, `size: u64`, `mode`, `executable`, `symlink_target` (empty when none). Derives `Clone`, `Debug`, `Default`, `PartialEq`, `Eq`. |
| `hash_symlink_target(&str) -> i64` | `SymlinkHash` | xxh3-64 over the UTF-8 bytes of the link target. |
| `permissions(&fs::Metadata) -> u32` | `Permissions` | The permission bits. On Windows 0755 for a directory and 0644 for all other entries. |
| `inspect(&Path, &str) -> anyhow::Result<Entry>` | `Inspect` | The entry of one file, directory or link. It does not follow a link. It hashes a file with `xxh3::hash_file`. |
| `inventory(&Path) -> anyhow::Result<Vec<Entry>>` | `Inventory` | The merged entries below a real directory. The root must not be a link. |
| `read(&Path) -> anyhow::Result<Vec<Entry>>` | `Read` | Reads and checks an inventory file, and returns the entries merged and sorted. It accepts only the shape that `write` writes: no unknown field, no missing or `null` field, no trailing data. A directory must not have the key `hash`, and a size must not be negative. |
| `write(&Path, &[Entry]) -> anyhow::Result<()>` | `Write` | Merges, then writes the file. It creates the missing parent directories with `create_dir_all_0755`. When `merge` rejects the entries, the file stays unchanged. |
| `merge<'a>(impl IntoIterator<Item = &'a Entry>) -> anyhow::Result<Vec<Entry>>` | `Merge` | Checks every entry, the spellings, the ancestors and the link graph. Returns the entries sorted by path. Pass `a.iter().chain(&b)` for several groups. |
| `create_dir_all_0755(&Path) -> io::Result<()>` | `os.MkdirAll(path, 0o755)` in filemetadata, jarpack and pluginpack | Creates the directory and its missing parents. Each new directory gets the mode 0755, also under the umask 002 or 077. The Go call applied the umask. A directory that exists keeps its mode. On Windows it only creates the directories. An error names the directory and keeps the `io::ErrorKind`. |
| `read_link_target(&Path) -> io::Result<String>` | `ReadLinkTarget` | The link target. A relative target comes back in slash form. |
