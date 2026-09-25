// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The helpers that the tests of the modes share.

use std::io::Write;
use std::path::{Path, PathBuf};

use zip::write::SimpleFileOptions;

use crate::OptionLine;

/// Returns the `testdata/` directory of this crate.
///
/// Bazel sets `DDT_TESTDATA_DIR` relative to the working directory of the test. `cargo test` does not set it, so the
/// directory beside `Cargo.toml` answers. The test reads `CARGO_MANIFEST_DIR` at run time, because the Bazel process
/// wrapper refuses a binary that embeds the absolute source path.
pub(crate) fn testdata_dir() -> PathBuf {
    std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
        || PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR")).join("testdata"),
        PathBuf::from,
    )
}

/// Returns the path of a file under `testdata/` as a string.
pub(crate) fn testdata(relative: &str) -> String {
    path_string(&testdata_dir().join(relative))
}

pub(crate) fn path_string(path: &Path) -> String {
    path.to_str().expect("a test path is valid UTF-8").to_owned()
}

/// Returns owned lines.
pub(crate) fn lines(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

/// Splits the lines into option lines, as `run` does.
pub(crate) fn option_lines(values: &[&str]) -> Vec<OptionLine> {
    crate::parse_option_lines(lines(values)).expect("the lines split into option lines")
}

/// Writes the lines into a flag file of the directory and runs the request, the way a rule passes it.
pub(crate) fn run_request(dir: &Path, request: &[String]) -> i32 {
    crate::run(&[format!("--flagfile={}", request_file(dir, request))])
}

pub(crate) fn write(path: &Path, content: &str) {
    std::fs::write(path, content).unwrap_or_else(|error| panic!("write {}: {error}", path.display()));
}

pub(crate) fn read(path: &Path) -> String {
    std::fs::read_to_string(path).unwrap_or_else(|error| panic!("read {}: {error}", path.display()))
}

pub(crate) fn read_bytes(path: &Path) -> Vec<u8> {
    std::fs::read(path).unwrap_or_else(|error| panic!("read {}: {error}", path.display()))
}

/// Writes a parameter file of the shape that the rules pass, and returns its path.
pub(crate) fn request_file(dir: &Path, lines: &[String]) -> String {
    let path = dir.join("arguments.txt");
    write(&path, &(lines.join("\n") + "\n"));
    path_string(&path)
}

/// Writes the declared build-number file, which is `@community//:build.txt` in the rule.
pub(crate) fn build_number_file(dir: &Path, value: &str) -> String {
    let path = dir.join("build.txt");
    write(&path, &format!("{value}\n"));
    path_string(&path)
}

/// Writes a jar with these entries, in this order, and returns its path.
pub(crate) fn descriptor_jar(dir: &Path, name: &str, entries: &[(&str, &str)]) -> String {
    let path = dir.join(name);
    let file = std::fs::File::create(&path).unwrap_or_else(|error| panic!("create {}: {error}", path.display()));
    let mut writer = zip::ZipWriter::new(file);
    for (entry, content) in entries {
        writer.start_file(*entry, SimpleFileOptions::default()).expect("start a jar entry");
        writer.write_all(content.as_bytes()).expect("write a jar entry");
    }
    writer.finish().expect("finish the jar");
    path_string(&path)
}

/// Returns a fresh temporary directory.
pub(crate) fn temp_dir() -> tempfile::TempDir {
    tempfile::tempdir().expect("create a temporary directory")
}

/// Asserts that the file does not exist.
pub(crate) fn assert_absent(path: &Path) {
    assert!(!path.exists(), "the failure wrote {}", path.display());
}
