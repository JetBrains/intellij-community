// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::fs;
use std::path::Path;

use super::testjar::{RawEntry, Scratch, entry, pack, raw, read_entry, write_raw_jar, write_zip_jar};
use crate::{INDEX_FILE_NAME, Jar, MergeSpec, Source};

fn opened_names(path: &Path) -> Vec<String> {
    let jar = Jar::open(path).unwrap_or_else(|error| panic!("{error}"));
    jar.entries().map(|entry| entry.name.to_string()).collect()
}

fn open_error(path: &Path) -> String {
    match Jar::open(path) {
        Ok(_) => panic!("{} was accepted", path.display()),
        Err(error) => format!("{error:#}"),
    }
}

#[test]
fn open_jar_keeps_central_directory_order_and_drops_what_is_never_inherited() {
    let scratch = Scratch::new();
    let path = write_zip_jar(
        &scratch,
        "source.jar",
        &[
            entry("z/", ""),
            entry("z/Last.class", "z"),
            entry(INDEX_FILE_NAME, "a stale index"),
            entry("a/First.class", "a"),
        ],
    );
    assert_eq!(opened_names(&path), ["z/Last.class", "a/First.class"]);
}

#[test]
fn open_jar_carries_the_central_directory_crc_of_a_deflated_entry() {
    // This is the point of the carried CRC. A zip CRC is defined over the uncompressed data. So the central directory
    // of a DEFLATED source already holds the value the STORED output needs. The stream writer writes a data descriptor,
    // so the CRC of the *local* header is zero here. A reader that took it from there would write a jar every JVM
    // rejects.
    let scratch = Scratch::new();
    let content = "the uncompressed bytes, which are what the CRC covers";
    let path = write_zip_jar(&scratch, "deflated.jar", &[entry("org/Deflated.class", content)]);
    let bytes = fs::read(&path).unwrap();
    assert_eq!(
        &bytes[14..18],
        &[0, 0, 0, 0],
        "the fixture must have a zero CRC in its local header"
    );
    let jar = Jar::open(&path).unwrap();
    let entries: Vec<_> = jar.entries().collect();
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].crc, crc32fast::hash(content.as_bytes()));
    assert_eq!(jar.data(&entries[0]).unwrap().as_ref(), content.as_bytes());
}

#[test]
fn data_refuses_a_truncated_deflate_stream() {
    // The central directory says the stream has half of its bytes. The inflater must fail, and not give a short entry.
    let scratch = Scratch::new();
    let content = "a line that the deflate stream holds\n".repeat(64);
    let path = write_zip_jar(&scratch, "truncated.jar", &[entry("org/Deflated.txt", &content)]);
    let mut data = fs::read(&path).unwrap();
    let eocd = data.len() - 22;
    let central_offset = u32::from_le_bytes(data[eocd + 16..eocd + 20].try_into().unwrap()) as usize;
    // Offset 20 of a central directory record is its compressed size.
    let field = central_offset + 20..central_offset + 24;
    let compressed = u32::from_le_bytes(data[field.clone()].try_into().unwrap());
    data[field].copy_from_slice(&(compressed / 2).to_le_bytes());
    fs::write(&path, data).unwrap();
    let jar = Jar::open(&path).unwrap();
    let entries: Vec<_> = jar.entries().collect();
    let error = format!("{:#}", jar.data(&entries[0]).unwrap_err());
    assert!(error.starts_with("org/Deflated.txt: inflating: "), "{error}");
}

#[test]
fn open_jar_takes_the_data_offset_from_the_local_header() {
    let scratch = Scratch::new();
    let path = write_raw_jar(
        &scratch,
        "asymmetric.jar",
        &[RawEntry {
            local_extra: &[0x55, 0x54, 5, 0, 1, 1, 2, 3, 4],
            central_extra: &[0x55, 0x54, 1, 0, 1],
            ..raw("org/Wide.class", "the real bytes")
        }],
    );
    let jar = Jar::open(&path).unwrap();
    let first = jar.entries().next().unwrap();
    assert_eq!(
        jar.data(&first).unwrap().as_ref(),
        b"the real bytes",
        "the central extra length was used"
    );
}

#[test]
fn open_jar_refuses_a_name_that_is_not_utf8() {
    // The Kotlin reader decodes the name to U+FFFD, so the two packers would hash the same entry differently. Refusal is
    // the only answer that cannot diverge silently.
    let scratch = Scratch::new();
    let path = write_raw_jar(
        &scratch,
        "latin1.jar",
        &[RawEntry {
            name: b"org/caf\xe9.properties",
            ..raw("", "value")
        }],
    );
    let error = open_error(&path);
    assert!(error.contains("not valid UTF-8"), "the error {error:?} must name the encoding");
    assert!(
        error.contains(r#""org/caf\xe9.properties""#),
        "the error {error:?} must quote the raw name"
    );
}

#[test]
fn open_jar_refuses_what_is_too_small_to_be_a_zip() {
    let scratch = Scratch::new();
    open_error(&scratch.file("truncated.jar", b"not a zip"));
    open_error(&scratch.file("empty.jar", b""));
}

#[test]
fn open_jar_refuses_a_file_with_no_end_record() {
    let scratch = Scratch::new();
    open_error(&scratch.file("garbage.jar", &[0; 512]));
}

#[test]
fn open_jar_refuses_a_central_directory_that_runs_past_its_end() {
    // A record whose name length reaches past the directory is a corrupt jar. The error names the file, and there is no
    // panic on a slice bound.
    let scratch = Scratch::new();
    let path = write_raw_jar(&scratch, "corrupt.jar", &[raw("org/Example.class", "bytes")]);
    let mut data = fs::read(&path).unwrap();
    let eocd = data.len() - 22;
    let central_offset = u32::from_le_bytes(data[eocd + 16..eocd + 20].try_into().unwrap()) as usize;
    // Offset 28 of a central directory record is its file name length.
    data[central_offset + 28..central_offset + 30].copy_from_slice(&0xff00u16.to_le_bytes());
    fs::write(&path, data).unwrap();
    let error = open_error(&path);
    assert!(error.contains("runs") && error.contains("corrupt.jar"), "error {error:?}");
}

#[test]
fn open_jar_refuses_a_zip64_source() {
    let scratch = Scratch::new();
    let path = write_raw_jar(&scratch, "zip64.jar", &[raw("org/Example.class", "bytes")]);
    let original = fs::read(&path).unwrap();
    let eocd = original.len() - 22;
    let mut count = original.clone();
    count[eocd + 10..eocd + 12].copy_from_slice(&0xffffu16.to_le_bytes());
    fs::write(&path, &count).unwrap();
    assert!(open_error(&path).contains("zip64"));
    let mut offset = original;
    offset[eocd + 16..eocd + 20].copy_from_slice(&0xffff_ffffu32.to_le_bytes());
    fs::write(&path, &offset).unwrap();
    assert!(open_error(&path).contains("zip64"));
}

#[test]
fn open_jar_refuses_a_local_extra_field_over_128_bytes() {
    let scratch = Scratch::new();
    let wide = [0u8; 129];
    let path = write_raw_jar(
        &scratch,
        "wide.jar",
        &[RawEntry {
            local_extra: &wide,
            ..raw("org/A.class", "a")
        }],
    );
    assert!(open_error(&path).contains("local extra field is 129 bytes"));
    let limit = [0u8; 128];
    let path = write_raw_jar(
        &scratch,
        "limit.jar",
        &[RawEntry {
            local_extra: &limit,
            ..raw("org/A.class", "a")
        }],
    );
    assert_eq!(opened_names(&path), ["org/A.class"]);
}

#[test]
fn open_jar_keeps_two_records_of_one_name_and_the_merge_writes_the_first() {
    // This is why the reader parses the central directory by hand. A reader that keys records by name keeps one record,
    // with the bytes of the last.
    let scratch = Scratch::new();
    let path = write_raw_jar(&scratch, "twice.jar", &[raw("org/A.class", "first"), raw("org/A.class", "second")]);
    assert_eq!(opened_names(&path), ["org/A.class", "org/A.class"]);
    let (data, duplicates) = pack(
        &scratch,
        MergeSpec {
            output: "out.jar".into(),
            sources: vec![Source::module(&path)],
            ..MergeSpec::default()
        },
    );
    assert_eq!(duplicates, ["org/A.class"]);
    assert_eq!(read_entry(&data, "org/A.class"), "first");
}
