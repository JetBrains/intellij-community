// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::cell::OnceCell;
use std::collections::HashSet;
use std::fmt;
use std::fs::{self, File};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use crate::error::{Error, IoContext, Result, bail, invalid};
use crate::nativelib::{self, extension};
use crate::natives::{NativeMerge, NativeSpec, file_name};
use crate::reader::Jar;
use crate::writer::{DirectoryMode, Writer};
use crate::{MANIFEST_ENTRY_NAME, library_filter, module_output_filter};

const ENTITIES_ENTRY_NAME: &str = "META-INF/listOfEntities.txt";

/// Selects the entries of one jar source by name.
pub type Filter = Arc<dyn Fn(&str) -> bool + Send + Sync>;

/// One input of a merge. It is a jar and the entries of it that belong in the result, or one file and its entry name.
///
/// The filter is per source because the two kinds of jar input are filtered differently. A module output gives almost
/// all it holds. A third-party library jar must lose its licences, signatures and multi-release `module-info` entries.
/// Otherwise several of them collide on one name, and the survivors ship for nothing.
///
/// [`Source::name`] selects the third kind. It is empty for a jar, whose entry names come from the jar. It is set for a
/// single file, whose bytes become one entry of that name. Then [`Source::filter`] is `None`, because a source of one
/// entry has nothing to select. The produced plugin descriptor of the dev distribution is that kind.
#[derive(Clone, Default)]
pub struct Source {
    pub path: PathBuf,
    pub filter: Option<Filter>,
    /// The entry name of a single-file source, and empty for a jar source.
    pub name: String,
    pub patch: bool,
    /// The manifest policy of this source. `None` uses [`MergeSpec::keep_manifest`].
    pub manifest: Option<ManifestMode>,
    /// Marks a `library=` source, the only kind a natives-mode group takes its native entries from.
    pub library: bool,
}

impl Source {
    /// A `module=` source: a module output jar with [`crate::module_output_name_filter`].
    pub fn module(path: impl Into<PathBuf>) -> Self {
        Self::archive(path, module_output_filter())
    }

    /// A `library=` source: a library jar with [`crate::library_name_filter`].
    pub fn library(path: impl Into<PathBuf>) -> Self {
        Self {
            library: true,
            ..Self::archive(path, library_filter())
        }
    }

    pub fn archive(path: impl Into<PathBuf>, filter: Filter) -> Self {
        Self {
            path: path.into(),
            filter: Some(filter),
            ..Self::default()
        }
    }

    /// A `file=` source: the bytes of one file as one entry.
    pub fn file(name: impl Into<String>, path: impl Into<PathBuf>) -> Self {
        Self {
            path: path.into(),
            name: name.into(),
            ..Self::default()
        }
    }

    /// A `patch=` source: a file source that must come before every other source of its entry.
    pub fn patch(name: impl Into<String>, path: impl Into<PathBuf>) -> Self {
        Self {
            patch: true,
            ..Self::file(name, path)
        }
    }

    /// Reports whether the manifest of this source can survive the merge.
    const fn keeps_manifest(&self, spec: &MergeSpec) -> bool {
        match self.manifest {
            None => spec.keep_manifest,
            Some(ManifestMode::Drop) => false,
            Some(ManifestMode::Keep | ManifestMode::CoverageAgent) => true,
        }
    }
}

impl fmt::Debug for Source {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Source")
            .field("path", &self.path)
            .field("filter", &self.filter.as_ref().map(|_| "Fn(&str) -> bool"))
            .field("name", &self.name)
            .field("patch", &self.patch)
            .field("manifest", &self.manifest)
            .field("library", &self.library)
            .finish()
    }
}

/// The manifest policy of one source.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum ManifestMode {
    Drop,
    Keep,
    /// Keeps the manifest of a coverage agent jar and points its `Boot-Class-Path` at the merged jar.
    CoverageAgent,
}

impl ManifestMode {
    /// Reads a policy as the recipes spell it. It refuses an unknown spelling and the Go `rewrite-boot-class-path`.
    pub fn parse(value: &str) -> Result<Self> {
        match value {
            "drop" => Ok(Self::Drop),
            "keep" => Ok(Self::Keep),
            "coverage-agent" => Ok(Self::CoverageAgent),
            "rewrite-boot-class-path" => {
                bail!("the manifest policy \"rewrite-boot-class-path\" is not supported: no recipe states it")
            }
            _ => bail!("unknown manifest policy {value:?}"),
        }
    }

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Drop => "drop",
            Self::Keep => "keep",
            Self::CoverageAgent => "coverage-agent",
        }
    }
}

/// One `output=` group of a flag file: a jar and the recipe it is built from.
#[derive(Clone, Debug, Default)]
#[expect(clippy::struct_excessive_bools, reason = "each field is one `yes` flag of the flag file")]
pub struct MergeSpec {
    pub output: PathBuf,
    pub sources: Vec<Source>,
    pub keep_manifest: bool,
    pub merge_entities: bool,
    pub reject_native_entries: bool,
    pub metadata_file: Option<PathBuf>,
    pub directory_mode: DirectoryMode,
    pub validate_entry_names: bool,
    /// The natives mode of the group, or `None`. See [`NativeSpec`].
    pub native: Option<NativeSpec>,
    /// The span file of the *run*. Nothing about packing reads it. It comes in the flag file because a packing action
    /// passes no other argument.
    pub trace_file: Option<PathBuf>,
    /// Calculates the CRC of every entry again instead of taking it from the source, and fails on a mismatch. It is off
    /// in a build and on in a parity run.
    pub verify_crc: bool,
}

/// What a merge did.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct MergeReport {
    /// The entry names that more than one source gave, in merge order.
    pub duplicates: Vec<String>,
    /// The size of the written jar.
    pub bytes_written: u64,
    /// The [`xxh3::hash_file`] value of the written jar. The merge hashes each byte as it goes to the file, so a caller
    /// gets the hash without a read of the jar.
    pub content_hash: i64,
}

impl MergeSpec {
    /// The file name of [`MergeSpec::output`], the `jar` tag of the `pack jar` span and the start of the duplicate line.
    pub fn jar_name(&self) -> String {
        file_name(&self.output)
    }

    /// Writes the jar this spec describes, and refuses a spec with no source.
    pub fn pack(&self) -> Result<MergeReport> {
        if self.sources.is_empty() {
            bail!("no inputs for {:?}", self.output);
        }
        self.merge()
    }

    /// Writes [`MergeSpec::output`] from the entries of the sources. It returns the names that more than one source
    /// gave, and the size and the content hash of the jar.
    ///
    /// **The first source wins**, so the source order is the precedence. To write what the in-process `JarPackager`
    /// writes, every library jar must come before every module output. Duplicates are expected, because two libraries
    /// can hold the same `META-INF/services` entry, so the merge reports a collision and does not fail.
    ///
    /// `keep_manifest` is the one policy that an entry name cannot state. A jar that merges several sources must not
    /// keep a manifest, because the survivor describes only one of them. A jar from one meaningful source keeps its
    /// own. The coverage-agent rewrite is the only change to the *content* of an entry. The manifest of such a source
    /// survives whatever `keep_manifest` and the filter say, because a manifest that must be rewritten must survive.
    pub fn merge(&self) -> Result<MergeReport> {
        let output = &self.output;
        let verify_crc = self.verify_crc;
        self.validate_sources()?;

        // Every source jar stays open until the output is closed. The duplicate set, the natives state and the entity
        // list hold names and entries of all of them. A cell per source opens each jar when the merge gets to it. The
        // Go merge did the same, so an error names the same source first.
        let jars: Vec<OnceCell<Jar>> = self.sources.iter().map(|_| OnceCell::new()).collect();
        let mut natives = match &self.native {
            Some(native) => Some(NativeMerge::new(self.native_source_index(native)?)),
            None => None,
        };
        if let Some(parent) = output.parent().filter(|parent| !parent.as_os_str().is_empty()) {
            filemeta::create_dir_all_0755(parent).map_err(Error::Bare)?;
        }
        let file = HashingWrite::new(File::create(output).at(output)?);

        let mut writer = Writer::with_directory_mode(file, self.directory_mode);
        let mut seen: HashSet<&str> = HashSet::new();
        let mut duplicates: Vec<String> = Vec::new();
        let mut entities: Vec<String> = Vec::new();

        for (i, source) in self.sources.iter().enumerate() {
            let keep_manifest = source.keeps_manifest(self);
            if !source.name.is_empty() {
                if let Some(native) = &self.native
                    && nativelib::is_native_entry(&source.name)
                {
                    bail!(
                        "{} contains native entry {} outside the native library {}",
                        source.path.display(),
                        source.name,
                        native.lib_name
                    );
                }
                if self.merge_entities && source.name == ENTITIES_ENTRY_NAME && !source.patch {
                    let data = fs::read(&source.path).at(&source.path)?;
                    entities.push(trim_entity_list(&data, &source.path)?.to_string());
                    continue;
                }
                if add_file_source(&mut writer, source, &mut seen, output, keep_manifest)? {
                    duplicates.push(source.name.clone());
                }
                continue;
            }

            let opened = Jar::open(&source.path)?;
            // The merge visits each source once, so the cell is empty here and takes the opened jar.
            let jar: &Jar = jars[i].get_or_init(move || opened);
            if let Some(natives) = natives.as_mut()
                && natives.index == i
            {
                natives.reserve(jar, source)?;
            }
            // The native source claims each of its native names and writes no bytes for them.
            let reserved = natives
                .as_ref()
                .filter(|natives| natives.index == i)
                .map(|natives| &natives.reserved);
            let filter = source
                .filter
                .as_ref()
                .expect("validate_sources requires a filter for an archive source");
            let coverage_agent = source.manifest == Some(ManifestMode::CoverageAgent);
            for entry in jar.entries() {
                if self.validate_entry_names {
                    distpath::validate_entry_name(entry.name).map_err(|error| invalid!("{error}").context(source.path.display()))?;
                }
                if self.reject_native_entries && is_residual_native_entry(entry.name) {
                    bail!(
                        "{} contains native entry {}; keep this jar with the Kotlin packer",
                        source.path.display(),
                        entry.name
                    );
                }
                if self.merge_entities && entry.name == ENTITIES_ENTRY_NAME {
                    let data = jar.data(&entry)?;
                    if verify_crc && crc32fast::hash(&data) != entry.crc {
                        bail!("{}: {}: source CRC does not match", source.path.display(), entry.name);
                    }
                    entities.push(trim_entity_list(&data, &source.path)?.to_string());
                    continue;
                }
                let is_manifest = entry.name == MANIFEST_ENTRY_NAME;
                let included = if is_manifest {
                    coverage_agent || (keep_manifest && filter(entry.name))
                } else {
                    filter(entry.name)
                };
                if !included {
                    continue;
                }
                // This check comes before the duplicate check. In natives mode a second copy of a native is an error,
                // not a collision. The tree is written from the native library alone.
                if let (Some(natives), Some(native)) = (&natives, &self.native)
                    && i != natives.index
                    && nativelib::is_native_entry(entry.name)
                {
                    bail!(
                        "{} contains native entry {} outside the native library {}",
                        source.path.display(),
                        entry.name,
                        native.lib_name
                    );
                }
                if !seen.insert(entry.name) {
                    duplicates.push(entry.name.to_string());
                    continue;
                }
                if reserved.is_some_and(|reserved| reserved.contains(entry.name)) {
                    continue;
                }

                let data = jar.data(&entry)?;
                if verify_crc {
                    // A carried CRC is sound only while the source CRC is right. No build pays for this check, but a
                    // parity run does: it proves that the copied number describes these bytes.
                    let actual = crc32fast::hash(&data);
                    if actual != entry.crc {
                        bail!(
                            "{}: {}: source CRC is {:08x} but its data hashes to {actual:08x}",
                            source.path.display(),
                            entry.name,
                            entry.crc
                        );
                    }
                }
                if coverage_agent && is_manifest {
                    let data = replace_coverage_agent(&data);
                    // The content changed, so this is the one entry whose CRC cannot come from the source. It also
                    // stays out of the package index. See Writer::add.
                    writer.add(entry.name, &data, crc32fast::hash(&data), false).at(output)?;
                    continue;
                }
                writer.add(entry.name, &data, entry.crc, true).at(output)?;
            }
        }

        if !entities.is_empty() {
            let data = entities.join("\n");
            writer
                .add(ENTITIES_ENTRY_NAME, data.as_bytes(), crc32fast::hash(data.as_bytes()), false)
                .at(output)?;
        }
        let closed_size = writer.close().at(output)?;
        let (content_hash, bytes_written) = writer.into_inner().at(output)?.finish();
        debug_assert_eq!(bytes_written, closed_size, "the file got another byte count than the writer wrote");
        // After the jar, so a tree never exists without its jar.
        if let (Some(natives), Some(native)) = (&natives, &self.native)
            && native.writes_tree()
        {
            let jar = jars[natives.index].get().expect("the native source was opened by the merge");
            self.write_native_tree(natives, jar, verify_crc)?;
        }
        Ok(MergeReport {
            duplicates,
            bytes_written,
            content_hash,
        })
    }

    fn validate_sources(&self) -> Result<()> {
        if let Some(native) = &self.native {
            native.validate(&self.output)?;
            if self.reject_native_entries {
                bail!(
                    "{}: a native tree and rejected native entries cannot be combined",
                    self.output.display()
                );
            }
        }
        for source in &self.sources {
            if !source.name.is_empty() {
                if self.validate_entry_names {
                    distpath::validate_entry_name(&source.name).map_err(|error| invalid!("{error}"))?;
                }
                if source.filter.is_some() || source.path.as_os_str().is_empty() {
                    bail!("invalid file source {:?}", source.name);
                }
                if source.manifest == Some(ManifestMode::CoverageAgent) {
                    bail!(
                        "the file source {:?} has the coverage-agent manifest policy, which only a jar source takes",
                        source.name
                    );
                }
            } else if source.filter.is_none() || source.path.as_os_str().is_empty() || source.patch {
                bail!("invalid archive source {:?}", source.path);
            }
        }
        Ok(())
    }
}

/// Writes a single-file source as one entry, and reports whether an earlier source already took the name.
///
/// It reads the whole file and does not map it. A single-file source is one small entry, so a map saves nothing.
///
/// The manifest rules of the merge apply unchanged, so a file source cannot add a manifest that `keep_manifest` drops.
/// No source states a CRC for a file, so its CRC is always calculated, and no CRC check applies.
fn add_file_source<'a>(
    writer: &mut Writer<impl Write>,
    source: &'a Source,
    seen: &mut HashSet<&'a str>,
    output: &Path,
    keep_manifest: bool,
) -> Result<bool> {
    if !source.patch && source.name == MANIFEST_ENTRY_NAME && !keep_manifest {
        return Ok(false);
    }
    if !seen.insert(&source.name) {
        if source.patch {
            bail!(
                "{}: patch {} must precede every source of that entry",
                output.display(),
                source.name
            );
        }
        return Ok(true);
    }

    let data = fs::read(&source.path).at(&source.path)?;
    writer.add(&source.name, &data, crc32fast::hash(&data), true).at(output)?;
    Ok(false)
}

/// The output of a merge: it passes each write on to the file, and hashes and counts the bytes that the file takes.
///
/// It sits between the write buffer of the [`Writer`] and the file, so it gets the buffer in chunks of up to 1 MiB, and
/// a larger entry in one chunk. [`xxh3::Hasher`] frames the stream in blocks of 256 KiB whatever the chunks are.
struct HashingWrite<W> {
    inner: W,
    hasher: xxh3::Hasher,
    bytes: u64,
}

impl<W: Write> HashingWrite<W> {
    const fn new(inner: W) -> Self {
        Self {
            inner,
            hasher: xxh3::Hasher::new(),
            bytes: 0,
        }
    }

    /// Returns the content hash and the byte count of what the file took, and closes the file.
    fn finish(self) -> (i64, u64) {
        (self.hasher.finish(), self.bytes)
    }
}

impl<W: Write> Write for HashingWrite<W> {
    fn write(&mut self, data: &[u8]) -> io::Result<usize> {
        let count = self.inner.write(data)?;
        self.hasher.update(&data[..count]);
        self.bytes += count as u64;
        Ok(count)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

/// The native entries that `reject-native-entries` refuses. It is the Go `isNativeEntry` of the merge, and it has no
/// `.tbd`, unlike [`nativelib::is_native_entry`].
fn is_residual_native_entry(name: &str) -> bool {
    matches!(extension(name), ".so" | ".dylib" | ".jnilib" | ".dll" | ".exe")
        || name.ends_with("pty4j-unix-spawn-helper")
        || name.ends_with("icudtl.dat")
}

/// Trims the characters that the Kotlin `String.trim()` removes: the Unicode white space without U+0085, and U+001C to
/// U+001F. The list holds class names, so the merge refuses a list that is not UTF-8.
pub(crate) fn trim_entity_list<'a>(data: &'a [u8], source: &Path) -> Result<&'a str> {
    let Ok(text) = std::str::from_utf8(data) else {
        bail!("{}: {ENTITIES_ENTRY_NAME} is not valid UTF-8", source.display());
    };
    Ok(text.trim_matches(|value: char| value != '\u{85}' && (value.is_whitespace() || ('\u{1c}'..='\u{1f}').contains(&value))))
}

/// The literal start of the `Boot-Class-Path` of a coverage agent jar.
const COVERAGE_AGENT_ATTRIBUTE: &[u8] = b"Boot-Class-Path: intellij-coverage-agent-";

/// The `Boot-Class-Path` that names the merged jar.
const MERGED_COVERAGE_AGENT_ATTRIBUTE: &[u8] = b"Boot-Class-Path: intellij.platform.coverage.agent.jar";

/// Points the manifest of a coverage agent at the jar it is in. The agent instruments from any class loader, and for
/// that the attribute must name its own jar. The merge into `lib/<module>.jar` renames it. A manifest without the
/// attribute comes back unchanged.
///
/// The function replaces each non-overlapping match of the Go pattern
/// `Boot-Class-Path: intellij-coverage-agent-\d+(\.\d+)*\.jar` from the left, as the Go `ReplaceAll` did. The Go `\d` is
/// ASCII only. It is written by hand, because a crate dependency in the packer re-keys every packing action when the
/// crate changes.
pub(crate) fn replace_coverage_agent(data: &[u8]) -> Vec<u8> {
    let mut replaced = Vec::with_capacity(data.len());
    let mut copied = 0;
    let mut search = 0;
    while let Some(start) = find(&data[search..], COVERAGE_AGENT_ATTRIBUTE).map(|offset| search + offset) {
        let version_start = start + COVERAGE_AGENT_ATTRIBUTE.len();
        match version_and_jar_length(&data[version_start..]) {
            Some(length) => {
                replaced.extend_from_slice(&data[copied..start]);
                replaced.extend_from_slice(MERGED_COVERAGE_AGENT_ATTRIBUTE);
                copied = version_start + length;
                search = copied;
            }
            None => search = start + 1,
        }
    }
    replaced.extend_from_slice(&data[copied..]);
    replaced
}

/// The length of `\d+(\.\d+)*\.jar` at the start of `text`, or `None`.
///
/// The greedy scan gives the only possible match. A shorter version leaves a digit or a `.` and a digit before `.jar`,
/// and `.jar` cannot start there.
fn version_and_jar_length(text: &[u8]) -> Option<usize> {
    let digits = |from: usize| text[from..].iter().take_while(|byte| byte.is_ascii_digit()).count();
    let mut end = digits(0);
    if end == 0 {
        return None;
    }
    while text.get(end) == Some(&b'.') {
        let more = digits(end + 1);
        if more == 0 {
            break;
        }
        end += 1 + more;
    }
    text[end..].starts_with(b".jar").then_some(end + ".jar".len())
}

/// The position of the first `needle` in `haystack`.
fn find(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|window| window == needle)
}
