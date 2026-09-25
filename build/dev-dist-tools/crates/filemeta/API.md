# filemeta

The file metadata of the dev-distribution tools: inventory JSON version 1, the xxh3 content hashes, and the path and
link checks. Port of the Go packages `internal/filemetadata` and `internal/xxh3`.

## File format

`{"version":1,"entries":[...]}` and one `\n`, with no indentation. The entries are sorted bytewise by `relativePath`.
An entry holds `relativePath, type, hash, size, mode, executable, symlinkTarget` in this order. A directory has no
`hash`. `symlinkTarget` is present only when it is not empty. `serde_json` writes the document.

## Supported subset

The crate supports the paths and links that the payloads of the repository have. It refuses all other input with an
error that names the input:

- A path or a link target must be ASCII without `<`, `>` and `&`. The message starts with `unsupported character`. For
  this text, `serde_json` writes the bytes of Go `json.Marshal`, and ASCII case folding is the Go path identity.
- A link target must not resolve through another link. The message starts with `unsupported symbolic link chain`. A
  cycle is also a chain.
- A link target must not have an empty segment, for example `payload/` or `lib//payload`. The message starts with
  `unsupported symbolic link target`. The Go composer refused such a target, and the Go `filemetadata` accepted it.

The checked-in dev plans, the tracked file names and the JCEF archives have no such name and no link chain. Their links
are relative file links with `..` segments and one directory link with a `./` prefix. No link in the git index has an
empty segment.

## Public items

| Item | Go original | Description |
|---|---|---|
| `enum EntryType { File, Directory, Symlink }` | `Entry.Type` | The JSON `type`: `file`, `directory` or `symlink`. |
| `struct Entry` | `Entry` | `relative_path`, `entry_type`, `hash`, `size`, `mode`, `executable`, `symlink_target` (empty when none). Derives `Clone`, `Debug`, `Default`, `PartialEq`, `Eq`. |
| `enum Error { Io { path, error }, Invalid(String) }` | `error` | `Io` names the path and holds the `io::Error`. `Invalid` holds the Go message text, or a text that names the unsupported input. |
| `hash_file(&Path) -> io::Result<i64>` | `HashFile` | xxh3-64 over 256 KiB blocks, each block followed by its length as 4 bytes little-endian (hash4j `putByteArray`). |
| `hash_symlink_target(&str) -> i64` | `SymlinkHash` | xxh3-64 over the UTF-8 bytes of the link target. |
| `permissions(&fs::Metadata) -> u32` | `Permissions` | The permission bits. On Windows 0755 for a directory and 0644 for all other entries. |
| `inspect(&Path, &str) -> Result<Entry, Error>` | `Inspect` | The entry of one file, directory or link. It does not follow a link. |
| `inventory(&Path) -> Result<Vec<Entry>, Error>` | `Inventory` | The merged entries below a real directory. The root must not be a link. |
| `read(&Path) -> Result<Vec<Entry>, Error>` | `Read` | Reads and checks an inventory file, and returns the entries merged and sorted. It accepts only the shape that `write` writes: no unknown field, no missing or `null` field, no trailing data. A directory must not have the key `hash`. |
| `write(&Path, &[Entry]) -> Result<(), Error>` | `Write` | Merges, then writes the file. It creates the missing parent directories with `fscopy::create_dirs_0755`. When `merge` rejects the entries, the file stays unchanged. |
| `merge<'a>(impl IntoIterator<Item = &'a Entry>) -> Result<Vec<Entry>, Error>` | `Merge` | Checks every entry, the spellings, the ancestors and the link graph. Returns the entries sorted by path. Pass `a.iter().chain(&b)` for several groups. |
| `validate_links(&BTreeMap<String, String>) -> Result<(), Error>` | `ValidateLinks` | Checks a set of links (link path to target) without file system access: escapes, aliases, a link below a link. Refuses a link chain and a target with an empty segment. |
| `validate_path(&str) -> Result<(), Error>` | `ValidatePath` | Accepts a relative slash path with no empty, `.` or `..` segment and no `\`, `:` or NUL. Refuses text outside the supported subset. |
| `path_identity(&str) -> Result<String, Error>` | `PathIdentity` | The path with ASCII letters in lowercase. Two paths with one identity collide on a case-insensitive file system. Refuses text outside the supported subset. |
| `clean_link_target(&str) -> String` | `CleanLinkTarget` | Removes `.` segments and repeated slashes from a relative target. Keeps `..`. Keeps an empty or absolute target. |
| `read_link_target(&Path) -> io::Result<String>` | `ReadLinkTarget` | The link target. A relative target comes back in slash form. |
| `xxh3::hash_bytes(&[u8]) -> i64` | `xxh3.HashBytes` | hash4j `hashBytesToLong`: XXH3-64, seed 0, over the bytes. |
| `xxh3::hash_chars(&str) -> i64` | `xxh3.HashChars` | hash4j `hashCharsToLong`: XXH3-64, seed 0, over the UTF-16LE code units, with no length. |
