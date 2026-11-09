use std::fs;

use testkit::{TempDir, read_text, write_file};

use crate::store::{BlobSource, STORE_DIR, Store, link_count};
use crate::test_support::names;

#[test]
fn a_full_blob_gets_a_second_copy() {
    let directory = TempDir::new();
    let source = directory.path().join("same.jar");
    write_file(&source, "same");
    let store = Store::new(directory.path()).unwrap().with_link_limit(3);
    let file = BlobSource {
        source: &source,
        hash: None,
        mode: 0o644,
    };
    for index in 0..4 {
        store.link(&file, &directory.path().join(format!("link-{index}"))).unwrap();
    }
    let blobs = names(&directory.path().join(STORE_DIR));
    let hash = xxh3::hash_file(&source).unwrap();
    assert_eq!(blobs, [format!("{hash:016x}-4-644"), format!("{hash:016x}-4-644.1")]);
    // The first blob took two links, and the second blob took the other two.
    assert_eq!(link_count(&directory.path().join(STORE_DIR).join(&blobs[0])).unwrap(), 3);
    assert_eq!(link_count(&directory.path().join(STORE_DIR).join(&blobs[1])).unwrap(), 3);
    for index in 0..4 {
        assert_eq!(read_text(directory.path().join(format!("link-{index}"))), "same");
    }
}

#[test]
fn the_mode_is_part_of_the_name_of_a_blob() {
    let directory = TempDir::new();
    let source = directory.path().join("tool");
    write_file(&source, "tool");
    let store = Store::new(directory.path()).unwrap();
    store
        .link(
            &BlobSource {
                source: &source,
                hash: None,
                mode: 0o644,
            },
            &directory.path().join("a"),
        )
        .unwrap();
    store
        .link(
            &BlobSource {
                source: &source,
                hash: None,
                mode: 0o755,
            },
            &directory.path().join("b"),
        )
        .unwrap();
    assert_eq!(names(&directory.path().join(STORE_DIR)).len(), 2);
}

#[test]
fn a_published_blob_stays_when_a_second_writer_links_it() {
    let directory = TempDir::new();
    let source = directory.path().join("app.jar");
    write_file(&source, "app");
    let hash = xxh3::hash_file(&source).unwrap();
    let store = Store::new(directory.path()).unwrap();
    let file = BlobSource {
        source: &source,
        hash: Some(hash),
        mode: 0o644,
    };
    store.link(&file, &directory.path().join("first")).unwrap();
    // Bazel replaces its output. The blob keeps the old bytes, and a link with the old hash reuses it.
    fs::remove_file(&source).unwrap();
    write_file(&source, "app");
    store.link(&file, &directory.path().join("second")).unwrap();
    assert_eq!(names(&directory.path().join(STORE_DIR)), [format!("{hash:016x}-3-644")]);
}
