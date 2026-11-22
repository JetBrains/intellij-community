use std::fs;

use testkit::{TempDir, require_error};

use super::*;

#[test]
fn read_layout_reads_the_file_of_the_composer() {
    let directory = TempDir::new();
    let path = directory.path().join(LOCAL_LAYOUT_FILE);
    fs::write(
        &path,
        r#"{"version":1,"files":[{"path":"bin","runfile":null,"symlinkTarget":null,"executable":false,"mode":493,"kind":"directory"},{"path":"bin/idea","runfile":"_main/idea","symlinkTarget":null,"executable":true,"mode":null,"hash":7}],"metadata":["fingerprint.txt"]}"#,
    )
    .unwrap();
    let layout = read_layout(&path).unwrap();
    assert_eq!(layout.metadata, ["fingerprint.txt"]);
    assert!(layout.files[0].is_directory());
    assert_eq!(layout.files[1].hash, Some(7));
}

#[test]
fn read_layout_refuses_another_version_and_a_missing_file() {
    let directory = TempDir::new();
    let path = directory.path().join(LOCAL_LAYOUT_FILE);
    require_error(read_layout(&path), "cannot read");
    fs::write(&path, r#"{"version":2,"files":[],"metadata":[]}"#).unwrap();
    require_error(read_layout(&path), "has the version 2, not 1");
    fs::write(&path, r#"{"version":1,"files":[],"metadata":[],"extra":1}"#).unwrap();
    require_error(read_layout(&path), "is not a local layout");
}
