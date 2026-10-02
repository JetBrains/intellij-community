// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::cell::OnceCell;
use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};

use crate::nativelib::{self, extension};
use crate::natives::{NativeMerge, NativeSpec, file_name};
use crate::reader::Jar;
use crate::writer::Writer;
use crate::{EntryFilter, MANIFEST_ENTRY_NAME};

const ENTITIES_ENTRY_NAME: &str = "META-INF/listOfEntities.txt";

/// One input of a merge: a jar and the entries of it that belong in the result, or one file and its entry name.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Source {
    /// A jar. Its entry names come from the jar, and `filter` selects the entries that belong in the result.
    Jar {
        path: PathBuf,
        filter: EntryFilter,
        /// The manifest policy of a library source. `None` uses [`MergeSpec::keep_manifest`]. A module output keeps its
        /// manifest whatever the policy is. See [`MergeSpec::merge`].
        manifest: Option<ManifestMode>,
    },
    /// The bytes of one file as the entry `name`. A source of one entry has nothing to select, so it has no filter. The
    /// produced plugin descriptor of the dev distribution is this kind.
    File {
        name: String,
        path: PathBuf,
        /// A patch must come before every other source of its entry.
        patch: bool,
        /// The manifest policy of this source. `None` uses [`MergeSpec::keep_manifest`]. A file source is never a module
        /// manifest, so the two manifest refusals of [`MergeSpec::merge`] do not apply to it.
        manifest: Option<ManifestMode>,
    },
}

impl Source {
    /// A `module=` source: a module output jar with [`EntryFilter::ModuleOutput`].
    pub fn module(path: impl Into<PathBuf>) -> Self {
        Self::Jar {
            path: path.into(),
            filter: EntryFilter::ModuleOutput,
            manifest: None,
        }
    }

    /// A `library=` source: a library jar with [`EntryFilter::Library`].
    pub fn library(path: impl Into<PathBuf>) -> Self {
        Self::Jar {
            path: path.into(),
            filter: EntryFilter::Library,
            manifest: None,
        }
    }

    /// A `file=` source: the bytes of one file as one entry.
    pub fn file(name: impl Into<String>, path: impl Into<PathBuf>) -> Self {
        Self::File {
            name: name.into(),
            path: path.into(),
            patch: false,
            manifest: None,
        }
    }

    /// A `patch=` source: a file source that must come before every other source of its entry.
    pub fn patch(name: impl Into<String>, path: impl Into<PathBuf>) -> Self {
        Self::File {
            name: name.into(),
            path: path.into(),
            patch: true,
            manifest: None,
        }
    }

    /// Returns this source with the manifest policy `mode`.
    #[must_use]
    pub const fn with_manifest(mut self, mode: ManifestMode) -> Self {
        match &mut self {
            Self::Jar { manifest, .. } | Self::File { manifest, .. } => *manifest = Some(mode),
        }
        self
    }

    /// The jar or the file that this source reads.
    pub fn path(&self) -> &Path {
        match self {
            Self::Jar { path, .. } | Self::File { path, .. } => path,
        }
    }

    pub(crate) const fn manifest(&self) -> Option<ManifestMode> {
        match self {
            Self::Jar { manifest, .. } | Self::File { manifest, .. } => *manifest,
        }
    }

    /// Reports whether the manifest of this source can survive the merge. The manifest of a module output always
    /// survives. For a library and a file, the policy of the source decides, and [`MergeSpec::keep_manifest`] decides
    /// when the source has no policy.
    const fn keeps_manifest(&self, spec: &MergeSpec) -> bool {
        if self.is_module_output() {
            return true;
        }
        match self.manifest() {
            None => spec.keep_manifest,
            Some(ManifestMode::Drop) => false,
            Some(ManifestMode::Keep) => true,
        }
    }

    /// Reports whether this source is a module output jar.
    const fn is_module_output(&self) -> bool {
        matches!(
            self,
            Self::Jar {
                filter: EntryFilter::ModuleOutput,
                ..
            }
        )
    }
}

/// The manifest policy of a library source or a file source. A module output ignores it.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum ManifestMode {
    Drop,
    Keep,
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
    pub validate_entry_names: bool,
    /// The natives mode of the group, or `None`. See [`NativeSpec`].
    pub native: Option<NativeSpec>,
}

/// The settings of a run, the same for every group that it packs.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct MergeOptions {
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
    /// The file name of [`MergeSpec::output`]. Every producer writes a jar under the name it has in the distribution, so
    /// this is that name. It is the `jar` tag of the `pack jar` span, the start of the duplicate line and the name that a
    /// `Boot-Class-Path` must state.
    pub fn jar_name(&self) -> String {
        file_name(&self.output)
    }

    /// Writes the jar this spec describes, and refuses a spec with no source.
    #[expect(
        clippy::unnecessary_debug_formatting,
        reason = "the Debug form quotes the path, and a refusal keeps its text"
    )]
    pub fn pack(&self, options: &MergeOptions) -> Result<MergeReport> {
        if self.sources.is_empty() {
            bail!("no inputs for {:?}", self.output);
        }
        self.merge(options)
    }

    /// Writes [`MergeSpec::output`] from the entries of the sources. It returns the names that more than one source
    /// gave, and the size and the content hash of the jar.
    ///
    /// **The first source wins**, so the source order is the precedence. To write what the in-process `JarPackager`
    /// writes, every library jar must come before every module output. Duplicates are expected, because two libraries
    /// can hold the same `META-INF/services` entry, so the merge reports a collision and does not fail.
    ///
    /// **A jar keeps the manifest of its module.** The manifest of a module output survives the merge, whatever
    /// `keep_manifest` and the policy of the source say. The manifest of a library survives only by its policy or by
    /// `keep_manifest`. A producer sets `keep_manifest` when the library is the one meaningful source of the jar. The
    /// merge refuses two module manifests in one jar. It also refuses a module manifest whose `Boot-Class-Path` main
    /// attribute is not the name of the jar, [`MergeSpec::jar_name`]. No entry changes its content in the merge.
    pub fn merge(&self, options: &MergeOptions) -> Result<MergeReport> {
        let output = &self.output;
        let jar_name = self.jar_name();
        let verify_crc = options.verify_crc;
        self.validate_sources()?;

        // Every source jar stays open until the output is closed. The duplicate set, the natives state and the entity
        // list hold names and entries of all of them. A cell per source opens each jar when the merge gets to it. The
        // Go merge did the same, so an error names the same source first.
        let jars: Vec<OnceCell<Jar>> = self.sources.iter().map(|_| OnceCell::new()).collect();
        let mut natives = match &self.native {
            Some(native) => Some(NativeMerge::new(self.native_source_index(native)?)),
            None => None,
        };
        let file = HashingWrite::new(File::create(output).with_context(|| output.display().to_string())?);

        let mut writer = Writer::new(file);
        let mut seen: HashSet<&str> = HashSet::new();
        let mut duplicates: Vec<String> = Vec::new();
        let mut entities: Vec<String> = Vec::new();
        let mut module_manifest: Option<&Path> = None;

        for (i, source) in self.sources.iter().enumerate() {
            let keep_manifest = source.keeps_manifest(self);
            let (path, filter) = match source {
                Source::File { name, path, patch, .. } => {
                    if let Some(native) = &self.native
                        && nativelib::is_native_entry(name)
                    {
                        bail!(
                            "{} contains native entry {name} outside the native library {}",
                            path.display(),
                            native.lib_name
                        );
                    }
                    if self.merge_entities && name == ENTITIES_ENTRY_NAME && !patch {
                        let data = fs::read(path).with_context(|| path.display().to_string())?;
                        entities.push(trim_entity_list(&data, path)?.to_string());
                        continue;
                    }
                    if add_file_source(&mut writer, name, path, *patch, &mut seen, output, keep_manifest)? {
                        duplicates.push(name.clone());
                    }
                    continue;
                }
                Source::Jar { path, filter, .. } => (path, *filter),
            };

            let opened = Jar::open(path)?;
            // The merge visits each source once, so the cell is empty here and takes the opened jar.
            let jar: &Jar = jars[i].get_or_init(move || opened);
            if let Some(natives) = natives.as_mut()
                && natives.index == i
            {
                natives.reserve(jar, path, filter)?;
            }
            // The native source claims each of its native names and writes no bytes for them.
            let reserved = natives
                .as_ref()
                .filter(|natives| natives.index == i)
                .map(|natives| &natives.reserved);
            for entry in jar.entries() {
                if self.validate_entry_names {
                    distpath::validate_entry_name(entry.name).with_context(|| path.display().to_string())?;
                }
                if self.reject_native_entries && is_residual_native_entry(entry.name) {
                    bail!(
                        "{} contains native entry {}; keep this jar with the Kotlin packer",
                        path.display(),
                        entry.name
                    );
                }
                if self.merge_entities && entry.name == ENTITIES_ENTRY_NAME {
                    let data = jar.data(&entry)?;
                    if verify_crc && crc32fast::hash(&data) != entry.crc {
                        bail!("{}: {}: source CRC does not match", path.display(), entry.name);
                    }
                    entities.push(trim_entity_list(&data, path)?.to_string());
                    continue;
                }
                let is_manifest = entry.name == MANIFEST_ENTRY_NAME;
                if (is_manifest && !keep_manifest) || !filter.accepts(entry.name) {
                    continue;
                }
                let is_module_manifest = is_manifest && filter == EntryFilter::ModuleOutput;
                if is_module_manifest {
                    if let Some(first) = module_manifest {
                        bail!(
                            "{}: two module manifests, from {} and {}",
                            output.display(),
                            first.display(),
                            path.display()
                        );
                    }
                    module_manifest = Some(path);
                }
                // This check comes before the duplicate check. In natives mode a second copy of a native is an error,
                // not a collision. The tree is written from the native library alone.
                if let (Some(natives), Some(native)) = (&natives, &self.native)
                    && i != natives.index
                    && nativelib::is_native_entry(entry.name)
                {
                    bail!(
                        "{} contains native entry {} outside the native library {}",
                        path.display(),
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
                            path.display(),
                            entry.name,
                            entry.crc
                        );
                    }
                }
                if is_module_manifest {
                    check_boot_class_path(&data, path, output, &jar_name)?;
                }
                at_output(writer.add(entry.name, &data, entry.crc, true), output)?;
            }
        }

        if !entities.is_empty() {
            let data = entities.join("\n");
            at_output(
                writer.add(ENTITIES_ENTRY_NAME, data.as_bytes(), crc32fast::hash(data.as_bytes()), false),
                output,
            )?;
        }
        let (file, closed_size) = at_output(writer.close(), output)?;
        let (content_hash, bytes_written) = file.finish();
        debug_assert_eq!(bytes_written, closed_size, "the file got another byte count than the writer wrote");
        // After the jar, so a tree never exists without its jar.
        if let (Some(natives), Some(tree)) = (&natives, self.native.as_ref().and_then(|native| native.tree.as_ref())) {
            let jar = jars[natives.index].get().expect("the native source was opened by the merge");
            self.write_native_tree(natives, tree, jar, verify_crc)?;
        }
        Ok(MergeReport {
            duplicates,
            bytes_written,
            content_hash,
        })
    }

    /// Checks what the type of [`Source`] cannot state: the entry names and the paths.
    #[expect(
        clippy::unnecessary_debug_formatting,
        reason = "the Debug form quotes the path, and a refusal keeps its text"
    )]
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
            match source {
                Source::File { name, path, .. } => {
                    if self.validate_entry_names {
                        distpath::validate_entry_name(name)?;
                    }
                    if path.as_os_str().is_empty() {
                        bail!("invalid file source {name:?}");
                    }
                }
                Source::Jar { path, .. } => {
                    if path.as_os_str().is_empty() {
                        bail!("invalid archive source {path:?}");
                    }
                }
            }
        }
        Ok(())
    }
}

/// Names the jar in an I/O failure of the writer, as the Go `write <path>: ...` error did. A [`Writer`] does not know
/// the path of its output. A refusal of the writer names its entry or its limit already and stays as it is.
fn at_output<T>(result: Result<T>, output: &Path) -> Result<T> {
    result.map_err(|error| {
        if error.is::<io::Error>() {
            error.context(output.display().to_string())
        } else {
            error
        }
    })
}

/// Writes a single-file source as one entry, and reports whether an earlier source already took the name.
///
/// It reads the whole file and does not map it. A single-file source is one small entry, so a map saves nothing.
///
/// The manifest rules of the merge apply unchanged, so a file source cannot add a manifest that `keep_manifest` drops.
/// No source states a CRC for a file, so its CRC is always calculated, and no CRC check applies.
fn add_file_source<'a>(
    writer: &mut Writer<impl Write>,
    name: &'a str,
    path: &Path,
    patch: bool,
    seen: &mut HashSet<&'a str>,
    output: &Path,
    keep_manifest: bool,
) -> Result<bool> {
    if !patch && name == MANIFEST_ENTRY_NAME && !keep_manifest {
        return Ok(false);
    }
    if !seen.insert(name) {
        if patch {
            bail!("{}: patch {name} must precede every source of that entry", output.display());
        }
        return Ok(true);
    }

    let data = fs::read(path).with_context(|| path.display().to_string())?;
    at_output(writer.add(name, &data, crc32fast::hash(&data), true), output)?;
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

/// The main attribute of a Java agent manifest that names the jar to add to the boot class path.
const BOOT_CLASS_PATH: &str = "Boot-Class-Path";

/// Refuses a module manifest whose `Boot-Class-Path` main attribute is not `jar_name`, the file name of the jar at
/// `output`. A Java agent names its own jar in this attribute, so the attribute must name the jar that the merge
/// writes. A manifest without the attribute passes.
pub(crate) fn check_boot_class_path(data: &[u8], source: &Path, output: &Path, jar_name: &str) -> Result<()> {
    let Ok(text) = std::str::from_utf8(data) else {
        bail!(
            "{}: the module manifest of {} is not valid UTF-8",
            output.display(),
            source.display()
        );
    };
    for value in main_attribute_values(text, BOOT_CLASS_PATH) {
        if value != jar_name {
            bail!(
                "{}: the module manifest of {} has `{BOOT_CLASS_PATH}: {value}`, but the jar is {jar_name}",
                output.display(),
                source.display()
            );
        }
    }
    Ok(())
}

/// Returns each value of the main attribute `name` of a manifest, in manifest order. The main section ends at the first
/// empty line. A line that starts with a space continues the line before it. The attribute name matches without regard
/// to ASCII case, as the JAR specification states.
pub(crate) fn main_attribute_values(text: &str, name: &str) -> Vec<String> {
    let mut lines: Vec<String> = Vec::new();
    for line in text.split('\n') {
        let line = line.strip_suffix('\r').unwrap_or(line);
        if line.is_empty() {
            break;
        }
        match (line.strip_prefix(' '), lines.last_mut()) {
            (Some(continuation), Some(last)) => last.push_str(continuation),
            _ => lines.push(line.to_string()),
        }
    }
    lines
        .iter()
        .filter_map(|line| {
            let (key, value) = line.split_once(':')?;
            key.eq_ignore_ascii_case(name).then_some(value)
        })
        .map(|value| value.strip_prefix(' ').unwrap_or(value).to_string())
        .collect()
}

#[cfg(test)]
mod tests;
