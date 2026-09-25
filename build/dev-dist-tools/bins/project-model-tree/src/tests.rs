// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use std::ffi::OsString;
use std::io::ErrorKind;
use std::path::{Path, PathBuf};

use crate::{CHUNK_SIZE, destination_in, materialize, run};

const ROW_SHAPES: &str = "is neither 'copy<TAB>source<TAB>destination' nor 'create<TAB><TAB>destination'";

fn write_source(dir: &Path, relative_path: &str, content: &str) -> String {
    let file = dir.join("sources").join(relative_path);
    std::fs::create_dir_all(file.parent().unwrap()).unwrap();
    std::fs::write(&file, content).unwrap();
    file.display().to_string()
}

fn write_manifest(dir: &Path, lines: &[String]) -> PathBuf {
    let manifest = dir.join("model.manifest");
    std::fs::write(&manifest, lines.join("\n") + "\n").unwrap();
    manifest
}

fn materialize_rows(dir: &Path, lines: &[String]) -> (PathBuf, anyhow::Result<usize>) {
    let target = dir.join("tree");
    let result = materialize(&write_manifest(dir, lines), &target);
    (target, result)
}

fn io_error_kind(error: &anyhow::Error) -> Option<ErrorKind> {
    error
        .chain()
        .find_map(|cause| cause.downcast_ref::<std::io::Error>())
        .map(std::io::Error::kind)
}

#[test]
fn copies_and_creates_at_the_declared_destinations() {
    let dir = tempfile::tempdir().unwrap();
    let source = write_source(
        dir.path(),
        "community/platform/build-scripts/intellij.platform.buildScripts.iml",
        "<module/>",
    );
    let (target, result) = materialize_rows(
        dir.path(),
        &[
            format!("copy\t{source}\tcommunity/platform/build-scripts/intellij.platform.buildScripts.iml"),
            "create\t\t.ultimate.root.marker".to_owned(),
            "create\t\tcommunity/.community.root.marker".to_owned(),
        ],
    );
    assert_eq!(result.unwrap(), 3);
    let content = std::fs::read_to_string(target.join("community/platform/build-scripts/intellij.platform.buildScripts.iml")).unwrap();
    assert_eq!(content, "<module/>");
    for marker in [".ultimate.root.marker", "community/.community.root.marker"] {
        let metadata = std::fs::metadata(target.join(marker)).unwrap_or_else(|error| panic!("marker {marker}: {error}"));
        assert_eq!(metadata.len(), 0);
    }
}

#[cfg(unix)]
#[test]
fn a_copy_keeps_the_permission_bits_and_follows_a_link() {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    let source = write_source(dir.path(), "bin/tool.sh", "#!/bin/sh\n");
    std::fs::set_permissions(&source, std::fs::Permissions::from_mode(0o750)).unwrap();
    let link = dir.path().join("sources/link.sh");
    std::os::unix::fs::symlink(&source, &link).unwrap();
    let (target, result) = materialize_rows(
        dir.path(),
        &[format!("copy\t{}\tbin/tool.sh", link.display()), "create\t\tmarker".to_owned()],
    );
    result.unwrap();
    let copied = target.join("bin/tool.sh");
    assert!(
        !std::fs::symlink_metadata(&copied).unwrap().file_type().is_symlink(),
        "the copy is a link"
    );
    assert_eq!(std::fs::metadata(&copied).unwrap().permissions().mode() & 0o777, 0o750);
    assert_eq!(std::fs::read_to_string(&copied).unwrap(), "#!/bin/sh\n");
    let mode = std::fs::metadata(target.join("marker")).unwrap().permissions().mode() & 0o777;
    assert_eq!(mode & !0o022, 0o644 & !0o022, "marker mode {mode:o}");
}

/// The manifest writer ends every row with a newline and writes no blank line.
#[test]
fn a_blank_line_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    let lines = ["create\t\t.ultimate.root.marker", ""].map(str::to_owned);
    let (target, result) = materialize_rows(dir.path(), &lines);
    let error = result.unwrap_err();
    assert_eq!(error.to_string(), format!("the project model manifest row \"\" {ROW_SHAPES}"));
    assert!(!target.exists(), "a refused manifest removed or wrote the tree");
}

#[test]
fn a_missing_source_fails() {
    let dir = tempfile::tempdir().unwrap();
    let missing = dir.path().join("sources/gone.iml");
    let (_, result) = materialize_rows(dir.path(), &[format!("copy\t{}\tgone.iml", missing.display())]);
    let error = result.unwrap_err();
    assert_eq!(
        io_error_kind(&error),
        Some(ErrorKind::NotFound),
        "expected a missing file error, got {error:#}"
    );
}

#[test]
fn a_destination_must_be_a_relative_path_of_plain_names() {
    let dir = tempfile::tempdir().unwrap();
    let source = write_source(dir.path(), "build.txt", "252.1");
    for destination in [
        "../escaped/build.txt",
        ".",
        "a/../..",
        "a/./b.iml",
        "a//b.iml",
        "",
        "/",
        "/etc/passwd",
        "a\\b.iml",
    ] {
        let (_, result) = materialize_rows(dir.path(), &[format!("copy\t{source}\t{destination}")]);
        let error = result.expect_err(destination);
        assert_eq!(
            error.to_string(),
            format!("the project model manifest destination {destination:?} is not a relative path of plain names")
        );
    }
    assert_eq!(
        destination_in(Path::new("/work/tree"), "community/a.iml").unwrap(),
        PathBuf::from("/work/tree/community/a.iml")
    );
}

#[test]
fn a_row_of_another_shape_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    for line in [
        "create\t.ultimate.root.marker",
        "create\tsource\t.ultimate.root.marker",
        "copy\t\ta.iml",
        "copy\tsource\ta.iml\textra",
        "link\t\tsomewhere",
    ] {
        let (_, result) = materialize_rows(dir.path(), &[line.to_owned()]);
        let error = result.expect_err(line);
        assert_eq!(error.to_string(), format!("the project model manifest row {line:?} {ROW_SHAPES}"));
    }
}

/// The refusal comes before the tree is removed, so the tree of the previous run stays.
#[test]
fn a_repeated_destination_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    let source = write_source(dir.path(), "a.iml", "a");
    let target = dir.path().join("tree");
    std::fs::create_dir_all(&target).unwrap();
    std::fs::write(target.join("previous.iml"), "<module/>").unwrap();
    let manifest = write_manifest(dir.path(), &[format!("copy\t{source}\ta.iml"), "create\t\ta.iml".to_owned()]);
    let error = materialize(&manifest, &target).unwrap_err();
    assert_eq!(
        error.to_string(),
        r#"the project model manifest names the destination "a.iml" more than once"#
    );
    assert!(target.join("previous.iml").exists(), "the tree was removed");
}

/// The tree is an output of the manifest alone, so the leftovers of a previous run must not survive into the next one.
#[test]
fn a_stale_tree_is_replaced_rather_than_merged_into() {
    let dir = tempfile::tempdir().unwrap();
    let source = write_source(dir.path(), "build.txt", "252.1");
    let target = dir.path().join("tree");
    std::fs::create_dir_all(target.join("community")).unwrap();
    std::fs::write(target.join("community/stale.iml"), "<module/>").unwrap();

    materialize(&write_manifest(dir.path(), &[format!("copy\t{source}\tbuild.txt")]), &target).unwrap();

    assert!(target.join("build.txt").exists());
    assert!(!target.join("community/stale.iml").exists(), "the stale file survived");
}

/// Many rows cross several chunks, so this exercises the workers and the order-independent result.
#[test]
fn many_rows_are_all_written() {
    let dir = tempfile::tempdir().unwrap();
    let mut lines = Vec::new();
    for index in 0..3 * CHUNK_SIZE + 7 {
        let letter = char::from(b'a' + (index % 26) as u8);
        let name = format!("m/{letter}/f{}-{index}.iml", "x".repeat(index % 5));
        lines.push(format!("copy\t{}\t{name}", write_source(dir.path(), &name, &index.to_string())));
    }
    let (target, result) = materialize_rows(dir.path(), &lines);
    assert_eq!(result.unwrap(), lines.len());
    for line in &lines {
        let fields: Vec<&str> = line.split('\t').collect();
        assert_eq!(
            std::fs::read(target.join(fields[2])).unwrap(),
            std::fs::read(fields[1]).unwrap(),
            "{}",
            fields[2]
        );
    }
}

struct Run {
    code: u8,
    output: String,
    errors: String,
}

fn run_tool(args: &[String]) -> Run {
    let mut output = Vec::new();
    let mut errors = Vec::new();
    let code = run(args.iter().map(OsString::from), &mut output, &mut errors);
    Run {
        code,
        output: String::from_utf8(output).unwrap(),
        errors: String::from_utf8(errors).unwrap(),
    }
}

#[test]
fn options() {
    for (args, expected) in [
        (vec!["--project-manifest=a"], "ERROR: --output-dir is required\n"),
        (vec!["--output-dir=a"], "ERROR: --project-manifest is required\n"),
        (vec!["--unknown=a"], "ERROR: unknown option \"--unknown\"\n"),
        (
            vec!["--output-dir=a", "--output-dir=b"],
            "ERROR: --output-dir must be specified at most once\n",
        ),
        (
            vec!["positional"],
            "ERROR: expected an option in the '--key=value' form, but got \"positional\"\n",
        ),
        (
            vec!["--output-dir="],
            "ERROR: expected an option in the '--key=value' form, but got \"--output-dir=\"\n",
        ),
        (
            vec!["--output-dir"],
            "ERROR: expected an option in the '--key=value' form, but got \"--output-dir\"\n",
        ),
    ] {
        let result = run_tool(&args.iter().map(|arg| (*arg).to_owned()).collect::<Vec<_>>());
        assert_eq!((result.code, result.errors.as_str()), (2, expected), "{args:?}");
    }
}

#[test]
fn run_writes_the_span_file() {
    let dir = tempfile::tempdir().unwrap();
    let manifest = write_manifest(dir.path(), &["create\t\t.ultimate.root.marker".to_owned()]);
    let tree = dir.path().join("tree");
    let trace = dir.path().join("spans.json");
    let result = run_tool(&[
        format!("--project-manifest={}", manifest.display()),
        format!("--output-dir={}", tree.display()),
        format!("--trace-file={}", trace.display()),
    ]);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(result.output, format!("Project model tree materialized into {}\n", tree.display()));
    let content = std::fs::read_to_string(&trace).unwrap();
    assert!(content.contains("materialize project model tree"), "span file: {content}");
    assert!(content.contains("\"files\""), "the root span lacks the file count: {content}");
}

#[test]
fn a_failed_run_still_writes_the_span_file() {
    let dir = tempfile::tempdir().unwrap();
    let manifest = write_manifest(dir.path(), &["link\t\tsomewhere".to_owned()]);
    let trace = dir.path().join("spans.json");
    let result = run_tool(&[
        format!("--project-manifest={}", manifest.display()),
        format!("--output-dir={}", dir.path().join("tree").display()),
        format!("--trace-file={}", trace.display()),
    ]);
    assert_eq!(result.code, 1);
    assert_eq!(
        result.errors,
        format!("ERROR: the project model manifest row \"link\\t\\tsomewhere\" {ROW_SHAPES}\n")
    );
    let content = std::fs::read_to_string(&trace).unwrap();
    assert!(
        content.contains("the project model manifest row"),
        "the span file lacks the failure: {content}"
    );
}
