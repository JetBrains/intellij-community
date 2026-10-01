//! The tests of `write_gzip_resources`.

use std::fs;

use super::*;
use crate::layout_archive::GZIP_MEMBER_HEADER;
use crate::write_gzip_resources;

fn expect_gzip_failure(archives: &[PathBuf], output: &Path, message: &str) {
    let error = write_gzip_resources(archives, output).unwrap_err();
    assert!(format!("{error:#}").contains(message), "expected {message:?}, got {error:#}");
}

#[test]
fn gzip_resources_accept_jar_files_and_keep_source_order() {
    let root = temp();
    let (first, second) = (root.path().join("first.jar"), root.path().join("second.zip"));
    write_zip(
        &first,
        &[zip_entry("a.xml", "a"), zip_entry("same.xml", "first"), zip_entry("dir/", "")],
    );
    write_zip(&second, &[zip_entry("b.xml", "b"), zip_entry("same.xml", "second")]);
    let output = root.path().join("resources");
    write_gzip_resources(&[first, second], &output).unwrap();
    for (name, want) in [("a.xml.gzip", "a"), ("same.xml.gzip", "first"), ("b.xml.gzip", "b")] {
        assert_eq!(text(&gunzip(&read_test_file(&output.join(name)))), want, "{name}");
    }
    assert_absent(&output.join("dir"));
    assert_eq!(
        read_test_file(&output.join("a.xml.gzip"))[..10],
        GZIP_MEMBER_HEADER,
        "the gzip header carries a name, a time, or an extra flag"
    );
}

#[test]
fn gzip_resources_keep_the_deflate_stream_of_the_source_entry() {
    let root = temp();
    let archive = root.path().join("resources.jar");
    let large = "<row/>".repeat(20000);
    write_zip(
        &archive,
        &[
            ZipEntry {
                deflate: true,
                ..zip_entry("deflated.xml", &large)
            },
            zip_entry("stored.xml", &large),
            zip_entry("empty.xml", ""),
        ],
    );
    let output = root.path().join("resources");
    write_gzip_resources(std::slice::from_ref(&archive), &output).unwrap();
    let mut crc = flate2::Crc::new();
    crc.update(large.as_bytes());
    let mut trailer = crc.sum().to_le_bytes().to_vec();
    trailer.extend_from_slice(&(large.len() as u32).to_le_bytes());
    let mut source = zip::ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
    let mut deflated = Vec::new();
    Read::read_to_end(&mut source.by_index_raw(0).unwrap(), &mut deflated).unwrap();
    let mut want = GZIP_MEMBER_HEADER.to_vec();
    want.extend_from_slice(&deflated);
    want.extend_from_slice(&trailer);
    let deflated_member = read_test_file(&output.join("deflated.xml.gzip"));
    assert_eq!(deflated_member, want, "the deflated member is not the source stream");
    assert_eq!(text(&gunzip(&deflated_member)), large);
    let stored = read_test_file(&output.join("stored.xml.gzip"));
    assert_eq!(stored[stored.len() - 8..], trailer[..], "the stored member trailer");
    assert_eq!(
        stored.len(),
        GZIP_MEMBER_HEADER.len() + large.len() + 2 * 5 + 8,
        "the stored member holds two stored blocks"
    );
    assert_eq!(text(&gunzip(&stored)), large);
    let mut empty = GZIP_MEMBER_HEADER.to_vec();
    empty.extend_from_slice(&[1, 0, 0, 0xff, 0xff, 0, 0, 0, 0, 0, 0, 0, 0]);
    let empty_member = read_test_file(&output.join("empty.xml.gzip"));
    assert_eq!(empty_member, empty, "the empty member");
    assert!(gunzip(&empty_member).is_empty());
}

#[test]
fn gzip_resources_read_zip_and_jar_archives_only() {
    let root = temp();
    let archive = root.path().join("resources.tar.gz");
    write_tar_gz(&archive, &[tar_file("a.xml", "a", 0o644)]);
    expect_gzip_failure(
        &[archive],
        &root.path().join("resources"),
        "a gzip resource source is a zip or jar archive",
    );
}

#[test]
fn gzip_resources_reject_an_entry_that_is_not_xml() {
    let root = temp();
    let archive = root.path().join("resources.jar");
    write_zip(&archive, &[zip_entry("a.xml", "a"), zip_entry("notes.txt", "text")]);
    expect_gzip_failure(&[archive], &root.path().join("resources"), r#"unexpected file "notes.txt""#);
    let linked = root.path().join("linked.jar");
    write_zip(&linked, &[zip_link("a.xml", "b.xml", 3)]);
    expect_gzip_failure(&[linked], &root.path().join("linked"), r#"unexpected file "a.xml""#);
}
