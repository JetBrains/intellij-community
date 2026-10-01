//! The fixtures that the tests of the modules share, and the tests that cross the modules. The tests of a module are in
//! `<module>/tests.rs`. The tests port the Go tests of `internal/pluginpack`, and each test module names its Go file. A
//! Go case without a port is named in the doc comment of the nearest test, with the reason. `corpus` and the tests of
//! `gzip_resources` have no Go original.

#![allow(
    clippy::cast_possible_truncation,
    clippy::cast_possible_wrap,
    clippy::unreadable_literal,
    reason = "a fixture writes small lengths into zip fields and compares known offsets"
)]

mod corpus;
mod derive;
mod kotlin;

use std::collections::BTreeMap;
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use planfile::contract::{
    Artifact, ArtifactKind, Asset, AssetKind, Catalogue, Filter, LayoutAsset, LayoutAssets, LayoutMapping, LayoutTransform,
    LayoutTransformKind, Manifest, Operation, Producer, Recipe, Reference, Source, TREE_VERSION, VERSION,
};
use sha2::Digest;
pub(crate) use testkit::{read_bytes, require_absent, testdata_dir, write_file};

use crate::plan;

pub(crate) fn temp() -> tempfile::TempDir {
    tempfile::tempdir().unwrap()
}

pub(crate) fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

pub(crate) fn chmod(path: &Path, mode: u32) {
    fscopy::set_mode(path, mode).unwrap();
}

/// Sets every directory of a source tree to 0755 and every regular file to 0644. The modes that a golden records are
/// then fixture facts and not umask facts. A fixture applies its own modes after this call.
pub(crate) fn chmod_tree(root: &Path) {
    for entry in walkdir::WalkDir::new(root) {
        let entry = entry.unwrap();
        if entry.file_type().is_dir() {
            chmod(entry.path(), 0o755);
        } else if entry.file_type().is_file() {
            chmod(entry.path(), 0o644);
        }
    }
}

pub(crate) fn symlink(target: impl AsRef<Path>, link: &Path) {
    let parent = link.parent().unwrap();
    fs::create_dir_all(parent).unwrap();
    let target_is_directory = parent.join(target.as_ref()).is_dir();
    fscopy::symlink(target.as_ref(), link, target_is_directory).unwrap();
}

pub(crate) fn mode_of(path: &Path) -> u32 {
    filemeta::permissions(&fs::symlink_metadata(path).unwrap())
}

pub(crate) fn exists(path: &Path) -> bool {
    fs::symlink_metadata(path).is_ok()
}

// The contract values of the tests.

pub(crate) fn remainder(destination: &str) -> Asset {
    Asset {
        destination: destination.to_owned(),
        producer: Producer::Remainder,
        ..Asset::default()
    }
}

pub(crate) fn independent(destination: &str, artifact: &str) -> Asset {
    Asset {
        destination: destination.to_owned(),
        producer: Producer::Independent,
        artifact: artifact.to_owned(),
        ..Asset::default()
    }
}

pub(crate) fn tree_asset(destination: &str) -> Asset {
    Asset {
        kind: AssetKind::Tree,
        class_path: Some(false),
        ..remainder(destination)
    }
}

pub(crate) fn file_artifact(id: &str, root: impl AsRef<Path>) -> Artifact {
    Artifact {
        id: id.to_owned(),
        kind: ArtifactKind::File,
        root: root.as_ref().to_string_lossy().into_owned(),
    }
}

pub(crate) fn directory_artifact(id: &str, root: impl AsRef<Path>) -> Artifact {
    Artifact {
        kind: ArtifactKind::Directory,
        ..file_artifact(id, root)
    }
}

pub(crate) fn catalogue(artifacts: Vec<Artifact>) -> Catalogue {
    Catalogue {
        version: VERSION,
        artifacts,
        libraries: Vec::new(),
    }
}

pub(crate) fn member(artifact: &str, path: &str) -> Reference {
    Reference {
        artifact: artifact.to_owned(),
        path: path.to_owned(),
    }
}

pub(crate) fn jar(destination: &str, sources: Vec<Source>) -> Operation {
    Operation::Jar {
        destination: destination.to_owned(),
        mode: 0o644,
        sources,
        merge_entities: false,
    }
}

pub(crate) fn archive_source(input: Reference, filter: Filter, manifest: Manifest) -> Source {
    Source::Archive { input, filter, manifest }
}

pub(crate) fn transform(kind: LayoutTransformKind) -> LayoutTransform {
    LayoutTransform {
        kind,
        strip_components: 0,
        mappings: Vec::new(),
        includes: Vec::new(),
        executables: Vec::new(),
    }
}

pub(crate) fn archive_tree(strip_components: u32, mappings: Vec<LayoutMapping>) -> LayoutTransform {
    LayoutTransform {
        strip_components,
        mappings,
        ..transform(LayoutTransformKind::ArchiveTree)
    }
}

pub(crate) fn mapping(pattern: &str, strip_components: u32, destination: &str) -> LayoutMapping {
    LayoutMapping {
        pattern: pattern.to_owned(),
        strip_components,
        destination: destination.to_owned(),
    }
}

pub(crate) fn layout_asset(destination: &str, sources: &[usize], transform: Option<LayoutTransform>) -> LayoutAsset {
    LayoutAsset {
        destination: destination.to_owned(),
        sources: sources.to_vec(),
        transform,
        mode: 0,
    }
}

pub(crate) fn layout(inputs: &[Reference], assets: Vec<LayoutAsset>) -> LayoutAssets {
    LayoutAssets {
        inputs: inputs.to_vec(),
        assets,
    }
}

/// One layout-tree operation with one tree asset.
pub(crate) fn layout_tree_recipe(destination: &str, layout: LayoutAssets) -> Recipe {
    Recipe {
        version: TREE_VERSION,
        plugin: "layout".to_owned(),
        layout_signature: "layout-v2".to_owned(),
        assets: vec![tree_asset(destination)],
        operations: vec![Operation::LayoutTree {
            destination: destination.to_owned(),
            layout,
        }],
    }
}

/// One jar from one layout source.
pub(crate) fn layout_jar_recipe(layout: LayoutAssets) -> Recipe {
    Recipe {
        version: VERSION,
        plugin: "layout".to_owned(),
        layout_signature: "layout-v1".to_owned(),
        assets: vec![remainder("lib/layout.jar")],
        operations: vec![jar("lib/layout.jar", vec![Source::Layout(layout)])],
    }
}

// The plans that several test modules share.

pub(crate) fn sample_plan(root: &Path) -> (Recipe, Catalogue) {
    let recipe = Recipe {
        version: VERSION,
        plugin: "example".to_owned(),
        layout_signature: "ordered-layout-v1".to_owned(),
        assets: vec![
            independent("lib/modules/separate.jar", "packed-separate"),
            remainder("lib/plugin.jar"),
        ],
        operations: vec![Operation::Jar {
            destination: "lib/plugin.jar".to_owned(),
            mode: 0o644,
            sources: vec![archive_source(Reference::artifact("module"), Filter::Module, Manifest::Drop)],
            merge_entities: true,
        }],
    };
    (recipe, catalogue(vec![file_artifact("module", root.join("module.jar"))]))
}

/// The sources of the single jar operation of a recipe.
pub(crate) fn jar_sources(recipe: &mut Recipe, index: usize) -> &mut Vec<Source> {
    match &mut recipe.operations[index] {
        Operation::Jar { sources, .. } => sources,
        operation => panic!("not a jar: {operation:?}"),
    }
}

// The execution of the tests.

/// The published plugin directory and inventory of one write. The temporary directory lives as long as the value.
pub(crate) struct Written {
    _root: tempfile::TempDir,
    pub(crate) output: PathBuf,
    pub(crate) inventory: Vec<filemeta::Entry>,
}

pub(crate) fn write_execution(recipe: &Recipe, catalogue: &Catalogue) -> Written {
    let execution = plan(recipe, catalogue).unwrap_or_else(|error| panic!("plan: {error:#}"));
    let root = temp();
    let output = root.path().join("plugin");
    let inventory = root.path().join("inventory.json");
    execution
        .write(&output, &inventory)
        .unwrap_or_else(|error| panic!("write: {error:#}"));
    let inventory = filemeta::read(&inventory).unwrap();
    Written {
        _root: root,
        output,
        inventory,
    }
}

pub(crate) fn expect_plan_error(recipe: &Recipe, catalogue: &Catalogue, message: &str) {
    match plan(recipe, catalogue) {
        Ok(_) => panic!("expected a plan failure with {message:?}"),
        Err(error) => assert!(format!("{error:#}").contains(message), "expected {message:?}, got {error:#}"),
    }
}

/// Plans the recipe, expects the write to fail with the message, and expects no published output.
pub(crate) fn expect_write_failure(recipe: &Recipe, catalogue: &Catalogue, message: &str) {
    let execution = plan(recipe, catalogue).unwrap_or_else(|error| panic!("plan: {error:#}"));
    let root = temp();
    match execution.write(&root.path().join("output"), &root.path().join("inventory.json")) {
        Ok(()) => panic!("expected a write failure with {message:?}"),
        Err(error) => assert!(format!("{error:#}").contains(message), "expected {message:?}, got {error:#}"),
    }
    assert_no_published_outputs(root.path());
}

pub(crate) fn assert_no_published_outputs(root: &Path) {
    for name in ["output", "inventory.json"] {
        assert!(!exists(&root.join(name)), "published partial output {name}");
    }
    let stages: Vec<String> = fs::read_dir(root)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .filter(|name| name.starts_with(".plugin-"))
        .collect();
    assert!(stages.is_empty(), "left staging files {stages:?}");
}

pub(crate) fn assert_content(file: &Path, want: &str) {
    let metadata = fs::symlink_metadata(file).unwrap_or_else(|error| panic!("{}: {error}", file.display()));
    assert!(metadata.is_file(), "{} is not a regular file", file.display());
    assert_eq!(String::from_utf8(read_bytes(file)).unwrap(), want, "{}", file.display());
}

pub(crate) fn assert_mode(file: &Path, want: u32) {
    assert_eq!(
        mode_of(file),
        want,
        "the mode of {} is {:o}, want {want:o}",
        file.display(),
        mode_of(file)
    );
}

pub(crate) fn assert_link(file: &Path, want: &str) {
    let target = fs::read_link(file).unwrap_or_else(|error| panic!("{}: {error}", file.display()));
    assert_eq!(target, Path::new(want), "{}", file.display());
}

// The archives of the tests.

/// One entry of [`zip_bytes`]. Creator 3 is Unix. Creator 19 is the MacOSX platform, which the readers treat as Unix
/// through its low nibble. Creator 10 is the Windows NTFS platform: it carries no mode and no link. Creator 0 states
/// no external attributes. An entry is STORED unless `deflate` is set.
#[derive(Clone, Default)]
pub(crate) struct ZipEntry {
    pub(crate) name: &'static str,
    pub(crate) content: String,
    pub(crate) mode: u32,
    pub(crate) symlink: bool,
    pub(crate) creator: u8,
    pub(crate) deflate: bool,
}

pub(crate) fn zip_entry(name: &'static str, content: &str) -> ZipEntry {
    ZipEntry {
        name,
        content: content.to_owned(),
        ..ZipEntry::default()
    }
}

pub(crate) fn unix_zip_entry(name: &'static str, content: &str, mode: u32, creator: u8) -> ZipEntry {
    ZipEntry {
        mode,
        creator,
        ..zip_entry(name, content)
    }
}

pub(crate) fn zip_link(name: &'static str, target: &str, creator: u8) -> ZipEntry {
    ZipEntry {
        symlink: true,
        ..unix_zip_entry(name, target, 0o777, creator)
    }
}

fn crc32(data: &[u8]) -> u32 {
    let mut crc = flate2::Crc::new();
    crc.update(data);
    crc.sum()
}

fn raw_deflate(data: &[u8]) -> Vec<u8> {
    let mut encoder = flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::default());
    encoder.write_all(data).unwrap();
    encoder.finish().unwrap()
}

/// Writes a zip with the sizes and the CRC in the local header and no data descriptor. The central directory states
/// the creator platform and the Unix mode of each entry.
pub(crate) fn zip_bytes(entries: &[ZipEntry]) -> Vec<u8> {
    let mut output = Vec::new();
    let mut central = Vec::new();
    for entry in entries {
        let content = entry.content.as_bytes();
        let data = if entry.deflate { raw_deflate(content) } else { content.to_vec() };
        let method: u16 = if entry.deflate { 8 } else { 0 };
        let crc = crc32(content);
        let name = entry.name.as_bytes();
        let offset = output.len() as u32;
        let date: u16 = 0x21;
        output.extend_from_slice(&0x0403_4b50u32.to_le_bytes());
        for value in [20u16, 0, method, 0, date] {
            output.extend_from_slice(&value.to_le_bytes());
        }
        for value in [crc, data.len() as u32, content.len() as u32] {
            output.extend_from_slice(&value.to_le_bytes());
        }
        output.extend_from_slice(&(name.len() as u16).to_le_bytes());
        output.extend_from_slice(&0u16.to_le_bytes());
        output.extend_from_slice(name);
        output.extend_from_slice(&data);
        let file_type = if entry.symlink {
            0o120000
        } else if entry.name.ends_with('/') {
            0o040000
        } else {
            0o100000
        };
        let external = if entry.creator == 0 { 0 } else { (entry.mode | file_type) << 16 };
        central.extend_from_slice(&0x0201_4b50u32.to_le_bytes());
        for value in [(u16::from(entry.creator) << 8) | 0x14, 20, 0, method, 0, date] {
            central.extend_from_slice(&value.to_le_bytes());
        }
        for value in [crc, data.len() as u32, content.len() as u32] {
            central.extend_from_slice(&value.to_le_bytes());
        }
        for value in [name.len() as u16, 0, 0, 0, 0] {
            central.extend_from_slice(&value.to_le_bytes());
        }
        central.extend_from_slice(&external.to_le_bytes());
        central.extend_from_slice(&offset.to_le_bytes());
        central.extend_from_slice(name);
    }
    let central_offset = output.len() as u32;
    output.extend_from_slice(&central);
    output.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
    for value in [0u16, 0, entries.len() as u16, entries.len() as u16] {
        output.extend_from_slice(&value.to_le_bytes());
    }
    output.extend_from_slice(&(central.len() as u32).to_le_bytes());
    output.extend_from_slice(&central_offset.to_le_bytes());
    output.extend_from_slice(&0u16.to_le_bytes());
    output
}

pub(crate) fn write_zip(file: &Path, entries: &[ZipEntry]) {
    write_file(file, zip_bytes(entries));
}

/// Writes a jar whose entries hold the names and contents in order, as Go `zip.Writer.Create` writes them: a file is
/// DEFLATED, a directory is STORED.
pub(crate) fn archive_file(file: &Path, entries: &[(&'static str, &str)]) {
    let entries: Vec<ZipEntry> = entries
        .iter()
        .map(|(name, content)| ZipEntry {
            deflate: !name.ends_with('/'),
            ..zip_entry(name, content)
        })
        .collect();
    write_zip(file, &entries);
}

/// One entry of [`write_tar_gz`]. A link makes a symbolic link, a hard link links to an earlier entry, and a name with
/// a trailing slash makes a directory. The name is written as it is, without normalization.
#[derive(Clone, Default)]
pub(crate) struct TarEntry {
    pub(crate) name: &'static str,
    pub(crate) content: &'static str,
    pub(crate) mode: u32,
    pub(crate) link: &'static str,
    pub(crate) hard_link: &'static str,
}

pub(crate) fn tar_file(name: &'static str, content: &'static str, mode: u32) -> TarEntry {
    TarEntry {
        name,
        content,
        mode,
        ..TarEntry::default()
    }
}

pub(crate) fn tar_directory(name: &'static str, mode: u32) -> TarEntry {
    tar_file(name, "", mode)
}

pub(crate) fn tar_link(name: &'static str, link: &'static str) -> TarEntry {
    TarEntry {
        name,
        link,
        ..TarEntry::default()
    }
}

pub(crate) fn tar_hard_link(name: &'static str, target: &'static str, mode: u32) -> TarEntry {
    TarEntry {
        name,
        hard_link: target,
        mode,
        ..TarEntry::default()
    }
}

pub(crate) fn write_tar_gz(file: &Path, entries: &[TarEntry]) {
    let encoder = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
    let mut builder = tar::Builder::new(encoder);
    for entry in entries {
        let mut header = tar::Header::new_ustar();
        let name = entry.name.as_bytes();
        header.as_old_mut().name[..name.len()].copy_from_slice(name);
        header.set_mode(entry.mode);
        header.set_mtime(0);
        let content = entry.content.as_bytes();
        let entry_type = if !entry.link.is_empty() {
            header.set_link_name_literal(entry.link).unwrap();
            tar::EntryType::Symlink
        } else if !entry.hard_link.is_empty() {
            header.set_link_name_literal(entry.hard_link).unwrap();
            tar::EntryType::Link
        } else if entry.name.ends_with('/') {
            tar::EntryType::Directory
        } else {
            tar::EntryType::Regular
        };
        let data: &[u8] = if entry_type == tar::EntryType::Regular { content } else { &[] };
        header.set_entry_type(entry_type);
        header.set_size(data.len() as u64);
        header.set_cksum();
        builder.append(&header, data).unwrap();
    }
    let data = builder.into_inner().unwrap().finish().unwrap();
    write_file(file, &data);
}

/// Writes the payload as one zstd frame.
pub(crate) fn write_zstd(file: &Path, payload: &[u8]) {
    let data = ruzstd::encoding::compress_to_vec(payload, ruzstd::encoding::CompressionLevel::Fastest);
    write_file(file, &data);
}

/// The entry names of a jar in central-directory order, and the content of each entry.
pub(crate) fn read_archive(file: &Path) -> (Vec<String>, BTreeMap<String, Vec<u8>>) {
    let mut archive = zip::ZipArchive::new(fs::File::open(file).unwrap()).unwrap();
    let mut names = Vec::new();
    let mut entries = BTreeMap::new();
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).unwrap();
        let mut data = Vec::new();
        entry.read_to_end(&mut data).unwrap();
        names.push(entry.name().to_owned());
        entries.insert(entry.name().to_owned(), data);
    }
    (names, entries)
}

pub(crate) fn text(data: &[u8]) -> String {
    String::from_utf8(data.to_vec()).unwrap()
}

/// Decompresses one gzip member.
pub(crate) fn gunzip(data: &[u8]) -> Vec<u8> {
    let mut decoded = Vec::new();
    flate2::read::GzDecoder::new(data).read_to_end(&mut decoded).unwrap();
    decoded
}

// The records of the tests.

pub(crate) fn sha256_hex(data: &[u8]) -> String {
    sha2::Sha256::digest(data).iter().map(|byte| format!("{byte:02x}")).collect()
}

/// Lists every entry of a written plugin directory in path order. A line holds the path, the kind, the mode, and the
/// content digest or the link target, tab-separated.
pub(crate) fn materialization_record(root: &Path) -> Vec<String> {
    let mut record = Vec::new();
    for entry in walkdir::WalkDir::new(root).min_depth(1).sort_by_file_name() {
        let entry = entry.unwrap();
        let relative = entry
            .path()
            .strip_prefix(root)
            .unwrap()
            .components()
            .map(|component| component.as_os_str().to_str().unwrap())
            .collect::<Vec<_>>()
            .join("/");
        let metadata = entry.metadata().unwrap();
        let mode = filemeta::permissions(&metadata);
        if metadata.is_file() {
            record.push(format!("{relative}\tfile\t{mode:04o}\t{}", sha256_hex(&read_bytes(entry.path()))));
        } else if metadata.file_type().is_symlink() {
            let target = fs::read_link(entry.path()).unwrap();
            record.push(format!("{relative}\tsymlink\t-\t{}", target.display()));
        } else if metadata.is_dir() {
            record.push(format!("{relative}\tdirectory\t{mode:04o}\t-"));
        } else {
            panic!("unsupported entry {}", entry.path().display());
        }
    }
    record
}

pub(crate) fn require_equal_records(what: &str, first: &[String], second: &[String]) {
    if first == second {
        return;
    }
    let only_first: Vec<&String> = first.iter().filter(|line| !second.contains(line)).collect();
    let only_second: Vec<&String> = second.iter().filter(|line| !first.contains(line)).collect();
    panic!("{what} differs\nonly in the first:\n{only_first:#?}\nonly in the second:\n{only_second:#?}");
}

/// Pins every inventory row to the file it describes.
pub(crate) fn require_inventory_matches_tree(output: &Path, inventory: &[filemeta::Entry]) {
    for entry in inventory {
        let actual = filemeta::inspect(&crate::paths::host(output, &entry.relative_path), &entry.relative_path)
            .unwrap_or_else(|error| panic!("{}: {error}", entry.relative_path));
        assert_eq!(&actual, entry, "the inventory differs from the tree");
    }
}

/// Pins that a fixture produced what it was written for, so an empty tree cannot pass parity.
pub(crate) fn require_recorded_paths(record: &[String], paths: &[&str]) {
    for path in paths {
        assert!(
            record.iter().any(|line| line.starts_with(&format!("{path}\t"))),
            "{path} is missing from the output:\n{}",
            record.join("\n")
        );
    }
}

/// Lists the entries of a jar by name with their content digest, sorted by name. The generated index follows the entry
/// order and is left out.
pub(crate) fn jar_entry_record(jar: &Path) -> Vec<String> {
    let (names, entries) = read_archive(jar);
    let mut record: Vec<String> = names
        .iter()
        .filter(|name| *name != "__index__")
        .map(|name| format!("{name}\tentry\t-\t{}", sha256_hex(&entries[name])))
        .collect();
    record.sort();
    record
}

/// The record of the Kotlin materialization of every fixture, frozen under `testdata/<name>-<label>.txt`. The label
/// states the recording date.
pub(crate) struct Golden {
    name: String,
    label: String,
    fixtures: BTreeMap<String, Vec<String>>,
}

impl Golden {
    pub(crate) fn open(name: &str) -> Self {
        let directory = testdata_dir();
        let matches: Vec<PathBuf> = fs::read_dir(&directory)
            .unwrap_or_else(|error| panic!("{}: {error}", directory.display()))
            .map(|entry| entry.unwrap().path())
            .filter(|path| {
                let file = path.file_name().unwrap().to_string_lossy();
                file.starts_with(&format!("{name}-")) && file.ends_with(".txt")
            })
            .collect();
        let [file] = matches.as_slice() else {
            panic!("expected one golden testdata/{name}-<label>.txt, found {matches:?}");
        };
        let file_name = file.file_name().unwrap().to_string_lossy().into_owned();
        let label = file_name[name.len() + 1..file_name.len() - 4].to_owned();
        let mut fixtures: BTreeMap<String, Vec<String>> = BTreeMap::new();
        for line in text(&read_bytes(file)).split('\n') {
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let (fixture, entry) = line
                .split_once('\t')
                .unwrap_or_else(|| panic!("{}: malformed line {line:?}", file.display()));
            fixtures.entry(fixture.to_owned()).or_default().push(entry.to_owned());
        }
        Self {
            name: name.to_owned(),
            label,
            fixtures,
        }
    }

    pub(crate) fn check(&self, fixture: &str, record: &[String]) {
        let Some(want) = self.fixtures.get(fixture) else {
            panic!("fixture {fixture:?} has no golden in testdata/{}-{}.txt", self.name, self.label);
        };
        require_equal_records(&format!("{fixture} against the golden"), want, record);
    }

    pub(crate) fn fixture_names(&self) -> Vec<&str> {
        self.fixtures.keys().map(String::as_str).collect()
    }
}
