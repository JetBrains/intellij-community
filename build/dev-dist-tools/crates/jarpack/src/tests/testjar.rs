// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The fixtures. Two different writers build them on purpose.
//!
//! The `zip` crate is an independent implementation. So a source jar it writes is the closest thing to a real Maven jar
//! a test can hold. Its stream writer writes DEFLATED data and a data descriptor with a zero CRC in the local header.
#![allow(clippy::cast_possible_truncation, reason = "the fixtures write small known values into zip fields")]
//! It also writes a flag word and a modification time that are not zero. The reader must survive all of that. A
//! hand-written fixture cannot express it without the assumptions of the reader. The timestamp is fixed, so it goes into
//! the fixture and not into the digest. That is also the claim of the packer about these fields.
//!
//! [`write_raw_jar`] exists for the things the `zip` crate cannot do. These are a *local* extra field that differs from
//! the central one, a name that is not UTF-8, and two records with one name.

use std::cell::Cell;
use std::fs::{self, File};
use std::io::{Cursor, Read, Write};
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};
use tempfile::TempDir;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, DateTime, ZipArchive, ZipWriter};

use crate::writer::INDEX_FORMAT_VERSION;
use crate::{EntryFilter, FlagFile, INDEX_FILE_NAME, MANIFEST_ENTRY_NAME, MergeOptions, MergeSpec, Source, parse_flag_file};

/// The temporary directories of one test. [`Scratch::dir`] returns a new empty directory, as the Go `t.TempDir()` did.
pub(crate) struct Scratch {
    root: TempDir,
    counter: Cell<u32>,
}

impl Scratch {
    pub(crate) fn new() -> Self {
        Self {
            root: tempfile::tempdir().expect("a temporary directory"),
            counter: Cell::new(0),
        }
    }

    pub(crate) fn dir(&self) -> PathBuf {
        let number = self.counter.get();
        self.counter.set(number + 1);
        let dir = self.root.path().join(number.to_string());
        fs::create_dir(&dir).expect("a new scratch directory");
        dir
    }

    /// Writes `data` to a file of this name in a new directory, and returns its path.
    pub(crate) fn file(&self, name: &str, data: &[u8]) -> PathBuf {
        let path = self.dir().join(name);
        fs::write(&path, data).expect("a scratch file");
        path
    }
}

/// One entry of a jar that the `zip` crate writes. A name that ends with `/` becomes a directory record.
#[derive(Clone, Copy)]
pub(crate) struct SourceEntry<'a> {
    pub(crate) name: &'a str,
    pub(crate) data: &'a str,
    pub(crate) stored: bool,
}

/// A DEFLATED entry.
pub(crate) const fn entry<'a>(name: &'a str, data: &'a str) -> SourceEntry<'a> {
    SourceEntry { name, data, stored: false }
}

/// Writes a jar with the stream writer of the `zip` crate, in the given order. That is the order of the central
/// directory, and so the order the packer must keep.
pub(crate) fn write_zip_jar(scratch: &Scratch, name: &str, entries: &[SourceEntry<'_>]) -> PathBuf {
    let path = scratch.dir().join(name);
    let mut writer = ZipWriter::new_stream(File::create(&path).expect("a fixture jar"));
    // Any time that is not zero, so that the fixture holds a modification time the packer must drop.
    let modified = DateTime::from_date_and_time(2026, 3, 4, 5, 6, 8).expect("a valid time");
    for entry in entries {
        let method = if entry.stored {
            CompressionMethod::Stored
        } else {
            CompressionMethod::Deflated
        };
        let options = SimpleFileOptions::default().compression_method(method).last_modified_time(modified);
        if entry.name.ends_with('/') {
            writer.add_directory(entry.name, options).expect("a directory record");
        } else {
            writer.start_file(entry.name, options).expect("a file record");
            writer.write_all(entry.data.as_bytes()).expect("the file data");
        }
    }
    writer.finish().expect("a finished fixture jar");
    path
}

/// One entry of a jar that [`write_raw_jar`] writes.
#[derive(Clone, Copy, Default)]
pub(crate) struct RawEntry<'a> {
    pub(crate) name: &'a [u8],
    pub(crate) data: &'a str,
    pub(crate) local_extra: &'a [u8],
    pub(crate) central_extra: &'a [u8],
}

pub(crate) fn raw<'a>(name: &'a str, data: &'a str) -> RawEntry<'a> {
    RawEntry {
        name: name.as_bytes(),
        data,
        ..RawEntry::default()
    }
}

/// Writes a STORED-only jar by hand, so that a local extra field can differ from the central one. Jars from other
/// tools do that, and the data of an entry starts after the *local* field.
pub(crate) fn write_raw_jar(scratch: &Scratch, name: &str, entries: &[RawEntry<'_>]) -> PathBuf {
    let mut out: Vec<u8> = Vec::new();
    let mut records = Vec::with_capacity(entries.len());
    for entry in entries {
        let data = entry.data.as_bytes();
        let crc = crc32fast::hash(data);
        records.push((entry, crc, out.len() as u32));
        out.extend_from_slice(&0x0403_4b50u32.to_le_bytes());
        out.extend_from_slice(&[20, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        out.extend_from_slice(&crc.to_le_bytes());
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(&(entry.name.len() as u16).to_le_bytes());
        out.extend_from_slice(&(entry.local_extra.len() as u16).to_le_bytes());
        out.extend_from_slice(entry.name);
        out.extend_from_slice(entry.local_extra);
        out.extend_from_slice(data);
    }
    let central_offset = out.len() as u32;
    for (entry, crc, offset) in &records {
        let data = entry.data.as_bytes();
        out.extend_from_slice(&0x0201_4b50u32.to_le_bytes());
        out.extend_from_slice(&[20, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        out.extend_from_slice(&crc.to_le_bytes());
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(&(entry.name.len() as u16).to_le_bytes());
        out.extend_from_slice(&(entry.central_extra.len() as u16).to_le_bytes());
        out.extend_from_slice(&[0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        out.extend_from_slice(&offset.to_le_bytes());
        out.extend_from_slice(entry.name);
        out.extend_from_slice(entry.central_extra);
    }
    let central_length = out.len() as u32 - central_offset;
    out.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
    out.extend_from_slice(&[0, 0, 0, 0]);
    out.extend_from_slice(&(records.len() as u16).to_le_bytes());
    out.extend_from_slice(&(records.len() as u16).to_le_bytes());
    out.extend_from_slice(&central_length.to_le_bytes());
    out.extend_from_slice(&central_offset.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    scratch.file(name, &out)
}

/// Runs a recipe and returns the bytes of the packed jar. Every case checks the CRCs: a carried CRC is sound only while
/// it describes its data, and in a test that check costs nothing. Every case also checks the size and the content hash
/// of the report against the file.
pub(crate) fn pack(scratch: &Scratch, mut spec: MergeSpec) -> (Vec<u8>, Vec<String>) {
    let file_name = spec.output.file_name().expect("an output file name").to_owned();
    spec.output = scratch.dir().join(file_name);
    // The caller creates the tree root, as the packer does.
    if let Some(tree) = spec.native.as_ref().and_then(|native| native.tree.as_ref()) {
        fs::create_dir_all(&tree.dir).expect("the tree root");
    }
    let report = spec
        .pack(&MergeOptions { verify_crc: true })
        .unwrap_or_else(|error| panic!("{error:#}"));
    let data = fs::read(&spec.output).expect("the packed jar");
    assert_eq!(report.bytes_written, data.len() as u64, "the size of {}", spec.output.display());
    let content_hash = xxh3::hash_file(&spec.output).expect("the hash of the packed jar");
    assert_eq!(report.content_hash, content_hash, "the content hash of {}", spec.output.display());
    (data, report.duplicates)
}

pub(crate) fn digest(data: &[u8]) -> String {
    Sha256::digest(data).iter().map(|byte| format!("{byte:02x}")).collect()
}

pub(crate) fn open_packed(data: &[u8]) -> ZipArchive<Cursor<&[u8]>> {
    ZipArchive::new(Cursor::new(data)).unwrap_or_else(|error| panic!("the zip crate cannot read the jar: {error}"))
}

/// Reads the packed jar back with the `zip` crate, so that an implementation that shares no code with the writer makes
/// the assertion.
pub(crate) fn entry_names(data: &[u8]) -> Vec<String> {
    let archive = open_packed(data);
    (0..archive.len())
        .map(|i| archive.name_for_index(i).expect("a name").to_string())
        .collect()
}

/// Returns the uncompressed content of one entry, read back through the `zip` crate, which also checks its CRC.
pub(crate) fn read_entry(data: &[u8], name: &str) -> String {
    let mut archive = open_packed(data);
    let mut file = archive
        .by_name(name)
        .unwrap_or_else(|error| panic!("{name:?} is not in the packed jar: {error}"));
    let mut content = String::new();
    file.read_to_string(&mut content).expect("the entry content");
    content
}

/// The offset in the 5-byte comment of the end record. The reader of the platform seeks back from it. -1 means the jar
/// holds no index.
pub(crate) fn index_pointer(data: &[u8]) -> i32 {
    assert!(data.len() >= 27, "{} bytes cannot hold an end record with a comment", data.len());
    let tail = &data[data.len() - 27..];
    assert_eq!(
        u32::from_le_bytes(tail[0..4].try_into().unwrap()),
        0x0605_4b50,
        "no end record 27 bytes from the end"
    );
    assert_eq!(u16::from_le_bytes([tail[20], tail[21]]), 5, "the comment length");
    assert_eq!(tail[22], INDEX_FORMAT_VERSION, "the index format version");
    i32::from_le_bytes(tail[23..27].try_into().unwrap())
}

// The recipes that several test modules share.

/// Returns the groups of a flag file with the text `lines`.
pub(crate) fn parse_recipe(scratch: &Scratch, lines: &str) -> anyhow::Result<Vec<MergeSpec>> {
    parse_recipe_file(scratch, lines).map(|flag_file| flag_file.groups)
}

pub(crate) fn parse_recipe_file(scratch: &Scratch, lines: &str) -> anyhow::Result<FlagFile> {
    let path = scratch.file("recipe.params", lines.as_bytes());
    parse_flag_file(&path, Path::new("/exec/root"))
}

pub(crate) const fn is_library(source: &Source) -> bool {
    matches!(
        source,
        Source::Jar {
            filter: EntryFilter::Library,
            ..
        }
    )
}

/// The entries of [`module_source`].
const MODULE_ENTRIES: [SourceEntry<'static>; 10] = [
    entry("com/", ""),
    entry("com/example/", ""),
    entry("com/example/Service.class", "class bytes"),
    entry("com/example/nested/Inner.class", "inner bytes"),
    entry("messages/Bundle.properties", "key=value"),
    entry(".unmodified", "dropped: compilation cache leftover"),
    entry(".hash", "dropped: compilation cache leftover"),
    entry("classpath.index", "dropped: compilation cache leftover"),
    entry("module-info.class", "dropped"),
    entry(INDEX_FILE_NAME, "dropped: a stale index is never inherited"),
];

/// What `jvm_library` gives the packer: a module output jar from Bazel. It has directory records, the compilation cache
/// leftovers that the filter drops, and what an earlier pack left behind. It has no manifest, because a module output has one only when
/// the module states it in its resources.
pub(crate) fn module_source(scratch: &Scratch, name: &str) -> PathBuf {
    write_zip_jar(scratch, name, &MODULE_ENTRIES)
}

/// The jar of [`module_source`] with a module manifest as its last entry.
pub(crate) fn module_source_with_manifest(scratch: &Scratch, name: &str) -> PathBuf {
    let mut entries = MODULE_ENTRIES.to_vec();
    entries.push(entry(MANIFEST_ENTRY_NAME, "Manifest-Version: 1.0\r\n\r\n"));
    write_zip_jar(scratch, name, &entries)
}

/// The manifest that [`agent_sources`] gives the module output. It has LF line ends, as a checked-in resource file has.
pub(crate) const AGENT_MANIFEST: &str = "Manifest-Version: 1.0\nPremain-Class: com.example.agent.Premain\n\
                                         Boot-Class-Path: intellij.example.agent.jar\nCan-Retransform-Classes: true\n";

/// A Java agent jar: a library with its own manifest, and the module output of the agent with the manifest of the
/// agent. The output jar is `intellij.example.agent.jar`.
pub(crate) fn agent_sources(scratch: &Scratch) -> (PathBuf, PathBuf) {
    let library = write_zip_jar(
        scratch,
        "asm-9.8.jar",
        &[
            entry("org/objectweb/asm/ClassReader.class", "reader"),
            entry(MANIFEST_ENTRY_NAME, "Manifest-Version: 1.0\r\nBundle-Name: asm\r\n\r\n"),
        ],
    );
    let module = write_zip_jar(
        scratch,
        "agent.jar",
        &[
            entry("com/example/agent/Premain.class", "premain"),
            entry(MANIFEST_ENTRY_NAME, AGENT_MANIFEST),
        ],
    );
    (library, module)
}
