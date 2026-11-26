// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The tests of the command line, the trace destination and the spans. Each test calls [`run`] as the
//! process does, with a scratch directory as the working directory. The bytes of the jars have their tests in
//! `jarpack`, where the bytes are. The inventory has its tests in `inventory/tests.rs`.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use serde_json::Value;
use tempfile::TempDir;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

use super::options;
use super::{FAILURE, run};

/// The content of the class in each fixture jar.
const CLASS: &[u8] = b"\xca\xfe\xba\xbenot really a class";

struct Outcome {
    code: u8,
    stderr: String,
}

fn run_in(base_dir: &Path, arguments: &[String]) -> Outcome {
    let mut stderr = Vec::new();
    let code = run(arguments.iter().map(OsString::from), base_dir, &mut stderr);
    Outcome {
        code,
        stderr: String::from_utf8(stderr).expect("a UTF-8 report"),
    }
}

/// The one argument of a packing action, for the recipe `recipe.txt` in `base_dir`.
fn flag_file_argument(base_dir: &Path) -> String {
    format!("--flagfile={}", base_dir.join("recipe.txt").display())
}

fn write_recipe(base_dir: &Path, recipe: &str) {
    fs::write(base_dir.join("recipe.txt"), recipe).expect("a recipe");
}

/// Writes a jar with the `zip` crate, which shares no code with the reader of `jarpack`. The inventory tests use it too.
pub(crate) fn write_jar(path: &Path, entries: &[(&str, &[u8])], method: CompressionMethod) {
    let mut writer = ZipWriter::new(File::create(path).expect("a fixture jar"));
    let options = SimpleFileOptions::default().compression_method(method);
    for (name, data) in entries {
        writer.start_file(*name, options).expect("an entry");
        writer.write_all(data).expect("the entry data");
    }
    writer.finish().expect("a closed fixture jar");
}

/// Writes a source jar and a recipe to a new directory, the working directory of the run. `extra` goes where the
/// packing rule puts its own lines, directly after `output=`.
fn pack_one_jar(extra: &str) -> TempDir {
    let dir = tempfile::tempdir().expect("a scratch directory");
    write_jar(
        &dir.path().join("module.jar"),
        &[("com/example/Packed.class", CLASS)],
        CompressionMethod::Deflated,
    );
    write_recipe(dir.path(), &format!("output=out/example.jar\n{extra}module=module.jar\n"));
    dir
}

/// Writes a jna-like library and a recipe in natives mode, as the platform jar rule declares them. The tree is the
/// directory `native` beside the jar, and the variant is macOS on arm. The spawn helper of pty4j is in the jar because
/// it is the one name without an extension that nativelib knows as a native.
fn pack_one_native_jar() -> TempDir {
    let dir = tempfile::tempdir().expect("a scratch directory");
    write_jar(
        &dir.path().join("jna-5.14.0.jar"),
        &[
            ("com/sun/jna/Native.class", CLASS),
            ("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", b"arm dispatch"),
            ("com/sun/jna/darwin-x86-64/libjnidispatch.jnilib", b"intel dispatch"),
            ("com/sun/jna/linux-x86-64/libjnidispatch.so", b"linux dispatch"),
            ("com/sun/jna/darwin-aarch64/pty4j-unix-spawn-helper", b"an executable"),
        ],
        CompressionMethod::Deflated,
    );
    write_recipe(
        dir.path(),
        "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\ntrace-file=jna.spans.json\n\
         native-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n",
    );
    dir
}

/// Returns each file under `dir` with its content.
fn snapshot(dir: &Path) -> BTreeMap<PathBuf, Vec<u8>> {
    fn walk(dir: &Path, files: &mut BTreeMap<PathBuf, Vec<u8>>) {
        for entry in fs::read_dir(dir).expect("a readable directory") {
            let path = entry.expect("a directory entry").path();
            if path.is_dir() {
                walk(&path, files);
            } else {
                let content = fs::read(&path).expect("a readable file");
                files.insert(path, content);
            }
        }
    }
    let mut files = BTreeMap::new();
    walk(dir, &mut files);
    files
}

fn file_names(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = fs::read_dir(dir)
        .expect("a readable directory")
        .map(|entry| entry.expect("a directory entry").file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    names
}

/// Reads a span file and returns its one trace.
fn read_trace(path: &Path) -> Value {
    let content = fs::read(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    let document: Value = serde_json::from_slice(&content).expect("a JSON span file");
    let data = document["data"].as_array().expect("a data array");
    assert_eq!(data.len(), 1, "want one trace in {document}");
    data[0].clone()
}

fn spans(trace: &Value) -> &[Value] {
    trace["spans"].as_array().expect("a span array")
}

fn operation_names(trace: &Value) -> Vec<&str> {
    spans(trace)
        .iter()
        .map(|span| span["operationName"].as_str().expect("an operation name"))
        .collect()
}

fn tags(span: &Value) -> BTreeMap<String, String> {
    let Some(tags) = span["tags"].as_array() else {
        return BTreeMap::new();
    };
    tags.iter()
        .map(|tag| {
            let text = |key: &str| tag[key].as_str().expect("a string field").to_owned();
            (text("key"), text("value"))
        })
        .collect()
}

fn owned(pairs: &[(&str, &str)]) -> BTreeMap<String, String> {
    pairs.iter().map(|(key, value)| (key.to_string(), value.to_string())).collect()
}

fn file_size(path: &Path) -> u64 {
    fs::metadata(path).expect("an existing file").len()
}

#[test]
fn the_option_surface_is_exactly_what_the_action_passes() {
    // The rule passes a --flagfile=, and no caller passes more. A typo must fail the action. The recipe names the trace
    // destination, so a `--trace-file=` is an unknown option too.
    let parse = |arguments: &[&str]| options::parse(arguments.iter().map(OsString::from));
    assert_eq!(parse(&["--flagfile=recipe.txt"]).unwrap(), PathBuf::from("recipe.txt"));
    let refused: [(&[&str], &str); 7] = [
        (&[], "--flagfile is required"),
        (&["--flagfile=recipe.txt", "--tracefile=x"], "unknown option: --tracefile"),
        (
            &["--flagfile=recipe.txt", "--trace-file=out/a.jar.spans.json"],
            "unknown option: --trace-file",
        ),
        (&["--flagfile=recipe.txt", "x"], "but got \"x\""),
        // The Go `flag` package took these forms. No caller passes them.
        (&["--flagfile", "recipe.txt"], "--flagfile takes a value"),
        (&["-flagfile=recipe.txt"], "in the form --key=value"),
        (&["--flagfile=a.txt", "--flagfile=b.txt"], "at most once"),
    ];
    for (arguments, want) in refused {
        let error = format!("{:#}", parse(arguments).expect_err("a refused command line"));
        assert!(error.contains(want), "{arguments:?}: the failure {error:?} must name {want:?}");
    }
    // The process reports an option error with the usage and fails.
    let dir = tempfile::tempdir().expect("a scratch directory");
    let outcome = run_in(dir.path(), &["--flagfile=recipe.txt".into(), "--tracefile=x".into()]);
    assert_eq!(outcome.code, FAILURE);
    assert_eq!(outcome.stderr, format!("ERROR: unknown option: --tracefile\n{}\n", options::USAGE));
}

#[test]
fn a_run_without_a_trace_file_writes_only_its_jar() {
    let dir = pack_one_jar("");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    assert_eq!(outcome.stderr, "");
    assert_eq!(file_names(&dir.path().join("out")), ["example.jar"]);
}

#[test]
fn natives_mode_tags_the_inventory_span() {
    // The inventory tests check the inventory itself. This test checks that the span shows the counters.
    let dir = pack_one_native_jar();
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    let trace = read_trace(&dir.path().join("jna.spans.json"));
    assert_eq!(
        operation_names(&trace),
        ["pack content modules", "pack jar", "inventory packing output"]
    );
    let tree = dir.path().join("out/native/aarch64");
    let byte_count = file_size(&dir.path().join("out/intellij.libraries.jna.jar"))
        + file_size(&tree.join("libjnidispatch.jnilib"))
        + file_size(&tree.join("pty4j-unix-spawn-helper"));
    let byte_count = byte_count.to_string();
    let want = [
        ("fileCount", "5"),
        ("hashedFileCount", "3"),
        ("byteCount", byte_count.as_str()),
        ("nativeFileCount", "2"),
    ];
    assert_eq!(tags(&spans(&trace)[2]), owned(&want));
}

/// The packer creates the parent of the jar and the tree root, and the pack creates the directories below the root. Each
/// gets the mode 0755, as the Go `os.MkdirAll(dir, 0o755)` gave it. The inventory records the mode of each tree
/// directory. The usual umask 022 also turns the 0777 default into 0755. So the test runs itself again in a child
/// process under the umask 002, where the two differ.
#[cfg(unix)]
#[test]
fn natives_mode_creates_each_directory_with_mode_0755() {
    use std::os::unix::fs::PermissionsExt;

    if testkit::rerun_under_shell("tests::natives_mode_creates_each_directory_with_mode_0755", &["umask 002"]).is_none() {
        return;
    }
    let dir = pack_one_native_jar();
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    for relative in ["out", "out/native", "out/native/aarch64"] {
        let path = dir.path().join(relative);
        let mode = fs::metadata(&path).unwrap().permissions().mode() & 0o777;
        assert!(mode == 0o755, "{relative}: the mode is {mode:o}, not 755");
    }
}

#[test]
fn natives_mode_refuses_a_trace_destination_inside_the_tree() {
    let dir = pack_one_native_jar();
    write_recipe(
        dir.path(),
        "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\ntrace-file=out/native\n\
         native-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n",
    );
    let before = snapshot(dir.path());
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(
        outcome.stderr.contains("a trace destination is another output"),
        "{}",
        outcome.stderr
    );
    assert_eq!(
        snapshot(dir.path()),
        before,
        "a refused trace destination changed the packing files"
    );
}

#[test]
fn natives_mode_fails_before_writing_when_the_recipe_is_incomplete() {
    let dir = pack_one_native_jar();
    write_recipe(
        dir.path(),
        "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\n\
         native-tree=out/native\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n",
    );
    let before = snapshot(dir.path());
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(outcome.stderr.contains("require each other"), "{}", outcome.stderr);
    assert_eq!(
        snapshot(dir.path()),
        before,
        "an incomplete natives recipe changed the packing files"
    );
}

#[test]
fn packing_rejects_absolute_metadata_aliases_before_writing() {
    // The Go parser cleaned an alias and then found the collision. The parser now refuses the alias itself.
    for alias in ["./", "unused/../"] {
        for destination in ["out/example.jar", "module.jar"] {
            let dir = pack_one_jar("");
            let base = dir.path();
            fs::create_dir(base.join("unused")).expect("a directory");
            fs::create_dir(base.join("out")).expect("a directory");
            fs::write(base.join("out/example.jar"), "existing jar").expect("an existing jar");
            let recipe = format!(
                "output={}\nmetadata-file={}/{alias}{destination}\ntrace-file=safe.spans.json\nmodule={}\n",
                base.join("out/example.jar").display(),
                base.display(),
                base.join("module.jar").display()
            );
            write_recipe(base, &recipe);
            let before = snapshot(base);
            let outcome = run_in(base, &[flag_file_argument(base)]);
            let case = format!("{alias}{destination}");
            assert_eq!(outcome.code, FAILURE, "{case}: {}", outcome.stderr);
            assert!(outcome.stderr.contains("has a `.` or `..` component"), "{case}: {}", outcome.stderr);
            assert_eq!(snapshot(base), before, "{case}: a refused metadata alias changed the packing files");
        }
    }
}

#[test]
fn packing_rejects_effective_trace_collisions_before_writing() {
    let destinations = [
        "out/example.jar",
        "example.metadata.json",
        "module.jar",
        "library.jar",
        "resource.txt",
        "plugin.xml",
    ];
    for destination in destinations {
        for variant in ["relative", "absolute", "absolute dot", "absolute parent"] {
            let dir = pack_one_jar("");
            let base = dir.path();
            fs::create_dir(base.join("unused")).expect("a directory");
            fs::copy(base.join("module.jar"), base.join("library.jar")).expect("a library jar");
            fs::write(base.join("resource.txt"), "resource").expect("a resource");
            fs::write(base.join("plugin.xml"), "<idea-plugin/>").expect("a descriptor");
            fs::write(base.join("example.metadata.json"), "existing metadata").expect("existing metadata");
            let trace_file = match variant {
                "absolute" => format!("{}/{destination}", base.display()),
                "absolute dot" => format!("{}/./{destination}", base.display()),
                "absolute parent" => format!("{}/unused/../{destination}", base.display()),
                _ => destination.to_owned(),
            };
            write_recipe(
                base,
                &format!(
                    "output=out/example.jar\nmetadata-file=example.metadata.json\ntrace-file={trace_file}\n\
                     module=module.jar\nlibrary=library.jar\nfile=resource.txt=resource.txt\npatch=META-INF/plugin.xml=plugin.xml\n"
                ),
            );
            let before = snapshot(base);
            let outcome = run_in(base, &[flag_file_argument(base)]);
            let case = format!("{destination}/{variant}");
            assert_eq!(outcome.code, FAILURE, "{case}: {}", outcome.stderr);
            // The Go packer cleaned an alias and then found the collision. The recipe parser refuses the alias itself.
            let refused = if variant.starts_with("absolute ") {
                outcome.stderr.contains("has a `.` or `..` component")
            } else {
                outcome.stderr.contains("a trace destination")
            };
            assert!(refused, "{case}: expected a trace collision, got {}", outcome.stderr);
            assert_eq!(
                snapshot(base),
                before,
                "{case}: a refused trace destination changed the packing files"
            );
        }
    }
}

#[test]
fn packing_metadata_read_metrics() {
    let dir = pack_one_jar("metadata-file=example.metadata.json\ntrace-file=example.spans.json\n");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    let trace = read_trace(&dir.path().join("example.spans.json"));
    assert_eq!(
        operation_names(&trace),
        ["pack content modules", "pack jar", "inventory packing output"]
    );
    let byte_count = file_size(&dir.path().join("out/example.jar")).to_string();
    let want = [("fileCount", "1"), ("hashedFileCount", "1"), ("byteCount", byte_count.as_str())];
    assert_eq!(tags(&spans(&trace)[2]), owned(&want));
}

#[test]
fn a_run_with_a_trace_file_describes_itself_in_it() {
    let dir = pack_one_jar("trace-file=out/example.jar.spans.json\n");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);

    // The path is relative, so it resolves against the working directory, which is the exec root in a build. The span
    // file is beside the jar.
    let trace = read_trace(&dir.path().join("out/example.jar.spans.json"));
    assert_eq!(trace["processes"]["p1"]["serviceName"], "content-module-packer");
    assert_eq!(operation_names(&trace), ["pack content modules", "pack jar"]);
    let (root, jar) = (&spans(&trace)[0], &spans(&trace)[1]);
    assert!(root.get("references").is_none(), "the root span has a parent: {root}");
    assert_eq!(tags(root), owned(&[]));
    let references = jar["references"].as_array().expect("a reference array");
    assert_eq!(references.len(), 1, "{jar}");
    assert_eq!(references[0]["refType"], "CHILD_OF");
    assert_eq!(references[0]["spanID"], root["spanID"]);

    // The `bytes` value is the size of the jar that this run packed.
    let bytes = file_size(&dir.path().join("out/example.jar")).to_string();
    let want = [("jar", "example.jar"), ("sources", "1"), ("bytes", bytes.as_str())];
    assert_eq!(tags(jar), owned(&want));
}

#[test]
fn a_trace_file_that_cannot_be_written_fails_the_request() {
    // The action declares the span file, so the run must fail.
    let dir = pack_one_jar("trace-file=module.jar/spans.json\n");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(
        outcome.stderr.starts_with("ERROR: writing the span file: "),
        "the report must say what failed: {}",
        outcome.stderr
    );
    // The run writes the jar before the trace, so the jar is there.
    assert!(dir.path().join("out/example.jar").is_file(), "the jar is missing");
}

#[test]
fn the_recipe_can_name_the_trace_destination() {
    // This is the channel of a build: the action passes only the flag file. The rule writes the line directly after
    // `output=`, and passes no option for it.
    let dir = pack_one_jar("trace-file=out/example.jar.spans.json\n");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    let content = fs::read_to_string(dir.path().join("out/example.jar.spans.json")).expect("a span file");
    for want in [
        r#""operationName":"pack content modules""#,
        r#""operationName":"pack jar""#,
        r#""key":"jar","type":"string","value":"example.jar""#,
    ] {
        assert!(content.contains(want), "{want} is missing from {content}");
    }
}

#[test]
fn a_recipe_that_does_not_parse_writes_no_trace() {
    // In a build, the destination is in the file that does not parse. The action fails, and Bazel discards the outputs
    // of a failed action.
    let dir = tempfile::tempdir().expect("a scratch directory");
    write_recipe(dir.path(), "modul=mod/a.jar\n");
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path())]);
    assert_eq!(outcome.code, FAILURE, "a recipe that does not parse must fail the run");
    assert!(outcome.stderr.starts_with("ERROR: "), "{}", outcome.stderr);
    assert_eq!(file_names(dir.path()), ["recipe.txt"], "the run wrote more than the recipe");
}

#[test]
fn a_duplicate_goes_to_the_span_and_a_run_that_succeeds_writes_nothing() {
    let dir = pack_one_jar("");
    let base = dir.path();
    fs::copy(base.join("module.jar"), base.join("copy.jar")).expect("a second module jar");
    write_recipe(
        base,
        "output=out/example.jar\nmetadata-file=example.metadata.json\ntrace-file=example.spans.json\n\
         module=module.jar\nmodule=copy.jar\n",
    );
    let outcome = run_in(base, &[flag_file_argument(base)]);
    assert_eq!((outcome.code, outcome.stderr.as_str()), (0, ""));
    let trace = read_trace(&base.join("example.spans.json"));
    let jar = tags(&spans(&trace)[1]);
    assert_eq!(jar.get("duplicates").map(String::as_str), Some("1"), "{jar:?}");
    assert_eq!(
        jar.get("duplicateReport").map(String::as_str),
        Some("example.jar: 1 duplicate entry, first source wins: com/example/Packed.class"),
        "{jar:?}"
    );
}

#[test]
fn the_duplicate_report_comes_before_the_error_of_a_failed_inventory() {
    let dir = pack_one_jar("");
    let base = dir.path();
    fs::copy(base.join("module.jar"), base.join("copy.jar")).expect("a second module jar");
    // A path under a file cannot be written, so the inventory fails after the merge.
    write_recipe(
        base,
        "output=out/example.jar\nmetadata-file=module.jar/metadata.json\ntrace-file=example.spans.json\n\
         module=module.jar\nmodule=copy.jar\n",
    );
    let outcome = run_in(base, &[flag_file_argument(base)]);
    assert_eq!(outcome.code, FAILURE, "packing succeeded without its declared metadata");
    let (report, error) = outcome.stderr.split_once('\n').expect("two lines");
    assert_eq!(
        report,
        "example.jar: 1 duplicate entry, first source wins: com/example/Packed.class"
    );
    assert!(error.starts_with("ERROR: "), "{}", outcome.stderr);

    // Both spans fail, and the jar span has no size.
    let trace = read_trace(&base.join("example.spans.json"));
    let jar = tags(&spans(&trace)[1]);
    let inventory = tags(&spans(&trace)[2]);
    for span in [&jar, &inventory] {
        assert_eq!(span.get("error").map(String::as_str), Some("true"), "{span:?}");
    }
    assert_eq!(jar.get("duplicates").map(String::as_str), Some("1"), "{jar:?}");
    assert!(!jar.contains_key("bytes"), "{jar:?}");
}

#[test]
fn a_recipe_of_two_jars_fails_before_writing() {
    let dir = pack_one_jar("");
    let base = dir.path();
    write_recipe(base, "output=out/a.jar\nmodule=module.jar\noutput=out/b.jar\nmodule=module.jar\n");
    let before = snapshot(base);
    let outcome = run_in(base, &[flag_file_argument(base)]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(outcome.stderr.contains("a recipe packs one jar"), "{}", outcome.stderr);
    assert_eq!(snapshot(base), before, "a refused recipe changed the packing files");
}
