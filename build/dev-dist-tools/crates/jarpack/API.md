# jarpack API

The packer core behind `PackContentModuleJar`. It merges source jars and single files into one STORED jar with a
generated `__index__`. It can also write the native files of one library as a tree. The output bytes are frozen. The six
golden digests in `src/tests/golden.rs` and the `./build/dev-dist.cmd jars` gate hold them.

It is the port of the Go packages `internal/jarpack` and `internal/nativelib`, and of `inventoryPackingOutput` in
`content-module-packer/main.go`. Every item below is `pub` at the crate root, except the items of `jarpack::nativelib`.

## Supported subset

The port takes only the inputs that a producer writes. The producers are `pack_jar` in `content_module_jar.bzl`, which
`content_module_jar`, `dev_dist_platform_jar` and `dev_plugin` call, the recipe replay of `build/dev-dist`, and the
plan files through pluginpack. Each other input fails with an error that names it.

| Input | Accepted | Refused |
| --- | --- | --- |
| `keep-manifest=`, `merge-entities=`, `reject-native-entries=`, `directory-entries=` | `true` | `false` and every other value |
| `source-manifest=` | `coverage-agent`, after a `module=` or a `library=` line | `keep`, `drop`, `rewrite-boot-class-path` |
| A flag-file path | a path without a `.` or `..` component | a path with one. The Go parser cleaned it. |
| `file=<entry name>=<path>` | a nonempty name and path. The recipe replay writes it for a single-file source. | no `=` after the name, or an empty part |
| `ManifestMode::parse` | `drop`, `keep`, `coverage-agent` | `rewrite-boot-class-path` and every other value |
| `DirectoryMode::parse` | `none`, `all` | `resources`, `""` and every other value |
| A file source | every policy except `coverage-agent` | `coverage-agent`. It applies to a jar source only. |
| `META-INF/listOfEntities.txt` with `merge_entities` | UTF-8 text | other bytes. The Go trim stopped at the first bad byte. |
| A native entry in `nativelib::select` | ASCII | other names. The family match folds ASCII case only. |

The one-shot mode with many `output=` groups stays, because the profiling method of the README uses it. No Starlark rule
writes a `file=` line. The recipe replay of `build/dev-dist` writes one, so the form stays.

## Packing

```rust
pub fn duplicate_line(jar_name: &str, duplicates: &[String]) -> Option<String>;
```

- A caller packs one spec in two steps. It calls `spec.pack()` first, and `write_inventory(&spec)` second when the
  spec names a metadata file. The `pack jar` span holds the first step, and its `inventory packing output` child span
  holds the second.
- `MergeSpec::pack` prints nothing. The caller prints `duplicate_line(&spec.jar_name(), &report.duplicates)` to stderr.
  The line is `<jar>: N duplicate entries, first source wins: a, b`, with at most 10 names, and it has no line end.
  The Go binary printed it before an inventory error, so the caller prints it between the two steps.
- `MergeSpec` and `MergeReport` are `Send + Sync`, so a caller can pack many specs in parallel with rayon.
- `MergeSpec::verify_crc` is the one switch of the CRC check.

## Recipes

```rust
pub fn parse_flag_file(path: &Path, base_dir: &Path) -> Result<Vec<MergeSpec>>;
pub fn resolve_path(value: &str, base_dir: &Path) -> Result<PathBuf>; // the path rule of parse_flag_file

#[derive(Clone, Debug, Default)]
pub struct MergeSpec {
    pub output: PathBuf,
    pub sources: Vec<Source>,
    pub keep_manifest: bool,
    pub merge_entities: bool,
    pub reject_native_entries: bool,
    pub metadata_file: Option<PathBuf>,
    pub directory_mode: DirectoryMode,
    pub validate_entry_names: bool,
    pub native: Option<NativeSpec>,
    pub trace_file: Option<PathBuf>,
    pub verify_crc: bool,
}
impl MergeSpec {
    pub fn jar_name(&self) -> String;           // the file name of output, the "jar" span tag
    pub fn merge(&self) -> Result<MergeReport>; // pluginpack calls this one
    pub fn pack(&self) -> Result<MergeReport>;  // refuses a spec with no source, then merges
}
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct MergeReport { pub duplicates: Vec<String>, pub bytes_written: u64 }

pub type Filter = Arc<dyn Fn(&str) -> bool + Send + Sync>;

#[derive(Clone, Default)] // and a manual Debug
pub struct Source {
    pub path: PathBuf,
    pub filter: Option<Filter>,         // Some for a jar source, None for a file source
    pub name: String,                   // empty for a jar source, the entry name for a file source
    pub patch: bool,
    pub manifest: Option<ManifestMode>, // None uses MergeSpec::keep_manifest
    pub library: bool,                  // a `library=` source
}
impl Source {
    pub fn module(path: impl Into<PathBuf>) -> Source;  // module_output_filter()
    pub fn library(path: impl Into<PathBuf>) -> Source; // library_filter(), library = true
    pub fn archive(path: impl Into<PathBuf>, filter: Filter) -> Source;
    pub fn file(name: impl Into<String>, path: impl Into<PathBuf>) -> Source;
    pub fn patch(name: impl Into<String>, path: impl Into<PathBuf>) -> Source;
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum ManifestMode { Drop, Keep, CoverageAgent }
impl ManifestMode { pub fn parse(value: &str) -> Result<Self>; pub fn as_str(self) -> &'static str }

pub fn module_output_name_filter(name: &str) -> bool;
pub fn library_name_filter(name: &str) -> bool;
pub fn module_output_filter() -> Filter;
pub fn library_filter() -> Filter;
pub const INDEX_FILE_NAME: &str = "__index__";
pub const MANIFEST_ENTRY_NAME: &str = "META-INF/MANIFEST.MF";
```

With `validate_entry_names`, the merge checks the name of each entry with `distpath::validate_entry_name` and keeps
its text. The natives mode checks the name of each tree file the same way.

`resolve_path` joins a relative value to `base_dir` and keeps an absolute one. It refuses a value with a `.` or `..`
component, because the recipe checks compare the paths as they are written. The packer applies it to `--trace-file=`.

`Filter` is an `Arc` closure, so a caller can compose a name filter with its own rule. The Go `Source.EntryOverrides`
has no port. Only natives mode set it, and natives mode now reserves its names inside the merge.

## Natives

```rust
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct NativeSpec {
    pub tree: Option<PathBuf>, // None when the spec only reserves
    pub family: Option<nativelib::Family>,
    pub arch: Option<nativelib::Arch>,
    pub lib_name: String,
}
impl NativeSpec { pub fn writes_tree(&self) -> bool; pub fn file_mode(&self, file_name: &str) -> u32 }
```

`jarpack::nativelib` is the port of `internal/nativelib`:

```rust
pub enum Family { Windows, MacOS, Linux }  // as_str(): "windows", "darwin", "linux"; Display
pub enum Arch { X64, AArch64, Universal }  // as_str(): "x64", "aarch64", ""; dir_name(); Display
pub struct Match { pub path_with_prefix: String, pub path: String, pub arch: Arch }
impl Match { pub fn file_name(&self) -> &str }

pub fn determine_arch(family: Family, entry_path: &str) -> Option<Arch>;
pub fn relative_path(lib_name: &str, arch: Arch, file_name: &str, entry_path: &str) -> Result<String>;
pub fn lib_name_from_file(file_name: &str) -> String;
pub fn is_native_entry(name: &str) -> bool;
pub fn is_executable(family: Option<Family>, file_name: &str) -> bool;
pub fn select<S: AsRef<str>>(entries: &[S], family: Family, arch: Arch) -> Result<Vec<Match>>;
pub fn parse_variant(variant: &str) -> Result<(Family, Arch)>;
pub fn valid_arch(arch: Arch) -> bool;
```

The Go `ValidFamily` has no port, because every `Family` value is valid.

## Inventory

```rust
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct InventoryReport {
    pub file_count: u64,                // "fileCount"
    pub hashed_file_count: u64,         // "hashedFileCount"
    pub byte_count: u64,                // "byteCount"
    pub native_file_count: Option<u64>, // "nativeFileCount", Some in natives mode only
}
pub fn write_inventory(spec: &MergeSpec) -> Result<InventoryReport>;
```

It writes the jar entry to `spec.metadata_file` through `filemeta`. In natives mode it also writes the tree root and
every entry under it. A tree file gets `NativeSpec::file_mode`, not the mode a stat returns. A tree directory gets the
mode that a stat returns. The merge creates each directory with `fscopy::create_dirs_0755`, so a new directory gets
the mode 0755 under any umask. The parent of the jar gets the same mode. The Go `os.MkdirAll(path, 0o755)` gave the
same mode under the umask 022 or 002, but 0700 under the umask 077. A directory that exists keeps its mode.

## Low level

```rust
pub struct Jar; // jarpack::reader::Jar
impl Jar {
    pub fn open(path: &Path) -> Result<Jar>;
    pub fn path(&self) -> &Path;
    pub fn entries(&self) -> impl ExactSizeIterator<Item = Entry<'_>> + '_;
    pub fn data(&self, entry: &Entry<'_>) -> Result<Cow<'_, [u8]>>;
}
#[derive(Clone, Copy, Debug)]
pub struct Entry<'a> { pub name: &'a str, pub crc: u32, pub size: u32 /* and private fields */ }

pub struct Writer<W: Write>; // jarpack::writer::Writer
impl<W: Write> Writer<W> {
    pub fn new(out: W) -> Self;
    pub fn with_directory_mode(out: W, mode: DirectoryMode) -> Self;
    pub fn add(&mut self, name: &str, data: &[u8], crc: u32, add_to_package_index: bool) -> Result<()>;
    pub fn close(&mut self) -> Result<u64>; // the size of the jar
    pub fn get_ref(&self) -> &W;
    pub fn into_inner(self) -> Result<W>;
}
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
pub enum DirectoryMode { None /* default */, All }
impl DirectoryMode { pub fn parse(value: &str) -> Result<Self>; pub fn as_str(self) -> &'static str }
```

The reader and the writer are written by hand, because a crate would break the frozen bytes. The `zip` crate keys the
records of a source jar by name. So it drops the second record of a name, and the merge must report that record. A general zip
writer writes the version, the flags and the times that the distribution jar keeps at zero.

## Errors

```rust
#[derive(Debug, thiserror::Error)]
pub enum Error {
    Invalid(String),
    Io { path: PathBuf, error: io::Error },
    Bare(io::Error), // an I/O error whose text is the whole message
    Context { context: String, error: Box<Error> },
}
impl From<filemeta::Error> for Error;
pub type Result<T, E = Error> = std::result::Result<T, E>;
```

The `Display` form of an `Invalid` and of a `Context` error keeps the text of the Go error. An `Io` error reads
`<path>: <io::Error>`, for example `x.jar: No such file or directory (os error 2)`. The Go text was
`open x.jar: no such file or directory`. No gate reads the text, and no test pins it. A refusal of the supported subset
has no Go counterpart, and its text names the refused input.

A `Writer` does not know the path of its output, so its I/O error is a `Bare` error without a path. The merge turns
it into an `Io` error with the jar path, for example `intellij.example.jar: No space left on device (os error 28)`.
The Go text was `write intellij.example.jar: no space left on device`. Another caller of `Writer` adds the path itself.
A directory that `fscopy` cannot create gives a `Bare` error, because the `fscopy` text names the directory already.
