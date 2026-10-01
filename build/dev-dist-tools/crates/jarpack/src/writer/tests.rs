// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The output format is the reason these headers are written by hand. Every field a general writer fills in is zero
//! here. So the bytes are a pure function of the name, the size and the CRC of each entry. The tests assert it through
//! the `zip` crate and do not assume it.

#![allow(
    clippy::cast_possible_truncation,
    clippy::cast_sign_loss,
    reason = "the fixtures write small known values into zip fields"
)]

use zip::{CompressionMethod, HasZipMetadata};

use crate::Writer;
use crate::index::IndexBuilder;
use crate::tests::testjar::{entry_names, open_packed};
use crate::writer::LOCAL_HEADER_SIZE;

fn write(names: &[&str]) -> (IndexBuilder, Vec<u8>) {
    let mut writer = Writer::new(Vec::new());
    for name in names {
        writer.add(name, name.as_bytes(), crc32fast::hash(name.as_bytes()), true).unwrap();
    }
    let (data, size, index) = writer.finish().unwrap();
    assert_eq!(size, data.len() as u64, "close returns the size of the jar");
    (index, data)
}

fn pointer_count(data: &[u8]) -> u32 {
    let pointer = index_pointer_of(data) as usize;
    u32::from_le_bytes(data[pointer - 5..pointer - 1].try_into().unwrap())
}

fn index_pointer_of(data: &[u8]) -> i32 {
    let tail = &data[data.len() - 27..];
    assert_eq!(
        u32::from_le_bytes(tail[0..4].try_into().unwrap()),
        0x0605_4b50,
        "no end record 27 bytes from the end"
    );
    i32::from_le_bytes(tail[23..27].try_into().unwrap())
}

#[test]
fn writer_emits_normalised_headers() {
    let mut writer = Writer::new(Vec::new());
    writer
        .add("com/example/Service.class", b"class bytes", crc32fast::hash(b"class bytes"), true)
        .unwrap();
    let (data, _) = writer.close().unwrap();

    let local = &data[..LOCAL_HEADER_SIZE];
    for (name, offset, size) in [
        ("version needed to extract", 4, 2),
        ("general purpose flags", 6, 2),
        ("compression method", 8, 2),
        ("modification time and date", 10, 4),
        ("extra field length", 28, 2),
    ] {
        assert!(
            local[offset..offset + size].iter().all(|&byte| byte == 0),
            "the local header {name} is not zero"
        );
    }

    let mut archive = open_packed(&data);
    for i in 0..archive.len() {
        let file = archive.by_index(i).unwrap();
        assert_eq!(file.compression(), CompressionMethod::Stored, "{} is not STORED", file.name());
        assert!(
            file.extra_data().is_none_or(<[u8]>::is_empty),
            "{} holds an extra field",
            file.name()
        );
        let metadata = file.get_metadata();
        // No unix mode gets into the archive.
        assert_eq!(metadata.external_attributes, 0, "{} holds external attributes", file.name());
        assert_eq!(metadata.flags, 0, "{} holds flags", file.name());
        assert_eq!(file.version_made_by(), (0, 0), "{} states a version", file.name());
    }
}

#[test]
fn writer_points_the_end_record_comment_into_the_index() {
    // Both entries are classes on purpose: a resource also registers its directory, and a registered directory takes an
    // entry-table slot of its own. See the index tests.
    let names = ["com/example/Service.class", "com/example/Other.class"];
    let (_, data) = write(&names);
    let pointer = index_pointer_of(&data);
    assert!(
        pointer > 0 && (pointer as usize) < data.len(),
        "the index pointer {pointer} is outside a {}-byte jar",
        data.len()
    );
    // The pointer is the first byte past the entry table, inside the index payload. So the entry count is directly
    // before it, and the count proves that the pointer is right.
    assert_eq!(pointer_count(&data), names.len() as u32);
}

#[test]
fn writer_writes_no_directory_record_and_indexes_resource_directories() {
    let (index, data) = write(&["classes/Value.class", "resources/nested/value.txt"]);
    let directories: Vec<String> = entry_names(&data).into_iter().filter(|name| name.ends_with('/')).collect();
    assert!(directories.is_empty(), "the archive holds directory records {directories:?}");
    let mut indexed: Vec<&[u8]> = Vec::new();
    for (position, entry) in index.entries.iter().enumerate() {
        let name = index.name(position);
        if entry.size != -1 {
            continue;
        }
        assert_eq!(entry.offset, 0, "a virtual directory record {entry:?}");
        indexed.push(name);
    }
    // The class directory gets no record. Each directory of the resource gets a virtual record.
    assert_eq!(indexed, [b"resources".as_slice(), b"resources/nested"]);
    assert_eq!(pointer_count(&data), index.entries.len() as u32);
}

#[test]
fn writer_rejects_long_names() {
    let mut writer = Writer::new(Vec::new());
    let error = writer.add(&"a".repeat(65536), b"", 0, true).unwrap_err();
    assert_eq!(format!("{error:#}"), "entry name exceeds the zip field limit");
}

#[test]
fn writer_writes_an_end_record_with_no_index_for_no_entry() {
    let (_, data) = write(&[]);
    assert_eq!(data.len(), 27);
    assert_eq!(index_pointer_of(&data), -1);
}

#[test]
fn writer_dropped_before_close_writes_nothing_more() {
    struct Shared(std::rc::Rc<std::cell::RefCell<Vec<u8>>>);
    impl std::io::Write for Shared {
        fn write(&mut self, data: &[u8]) -> std::io::Result<usize> {
            self.0.borrow_mut().extend_from_slice(data);
            Ok(data.len())
        }
        fn flush(&mut self) -> std::io::Result<()> {
            Ok(())
        }
    }
    let written = std::rc::Rc::new(std::cell::RefCell::new(Vec::new()));
    let mut writer = Writer::new(Shared(written.clone()));
    writer.add("a.txt", b"a", crc32fast::hash(b"a"), true).unwrap();
    drop(writer);
    assert!(
        written.borrow().is_empty(),
        "the Go writer never flushed a writer that failed before Close"
    );
}
