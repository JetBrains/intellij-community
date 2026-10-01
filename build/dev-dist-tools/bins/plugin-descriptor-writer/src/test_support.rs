// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The helpers that the tests of the modes share.

use std::ffi::OsString;
use std::io::Write;
use std::path::Path;

use testkit::write_file;
use zip::write::SimpleFileOptions;

/// Returns the path of a file under `testdata/` as a string.
pub(crate) fn testdata(relative: &str) -> String {
    path_string(&testkit::testdata(relative))
}

pub(crate) fn path_string(path: &Path) -> String {
    path.to_str().expect("a test path is valid UTF-8").to_owned()
}

/// Returns owned lines.
pub(crate) fn lines(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

/// Parses the lines of a flag file, as `run` does.
pub(crate) fn option_lines(values: &[&str]) -> cli::Options {
    cli::parse(values.iter().map(OsString::from)).expect("the lines parse")
}

/// Parses the lines of a flag file and takes the mode flag, as `run` does before it hands the request to a mode.
pub(crate) fn mode_request(values: &[&str]) -> anyhow::Result<cli::Options> {
    let mut options = cli::parse(values.iter().map(OsString::from))?;
    crate::take_mode(&mut options)?;
    Ok(options)
}

/// Runs the tool with these arguments.
pub(crate) fn run_arguments(arguments: &[String]) -> i32 {
    crate::run(arguments.iter().map(OsString::from))
}

/// Writes the lines into a flag file of the directory and runs the request, the way a rule passes it.
pub(crate) fn run_request(dir: &Path, request: &[String]) -> i32 {
    run_arguments(&[format!("--flagfile={}", request_file(dir, request))])
}

/// Writes a parameter file of the shape that the rules pass, and returns its path.
pub(crate) fn request_file(dir: &Path, lines: &[String]) -> String {
    let path = dir.join("arguments.txt");
    write_file(&path, lines.join("\n") + "\n");
    path_string(&path)
}

/// Writes the declared build-number file, which is `@community//:build.txt` in the rule.
pub(crate) fn build_number_file(dir: &Path, value: &str) -> String {
    let path = dir.join("build.txt");
    write_file(&path, format!("{value}\n"));
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
