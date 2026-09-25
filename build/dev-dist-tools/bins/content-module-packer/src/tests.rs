// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The tests of the command line, the trace destination, the spans and the parallel run. Each test calls [`run`] as the
//! process does, with a scratch directory as the working directory. The bytes of the jars and the inventories have
//! their tests in `jarpack`, where the bytes are.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use serde_json::Value;
use tempfile::TempDir;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

use super::options::{self, Options};
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

/// Writes a jar with the `zip` crate, which shares no code with the reader of `jarpack`.
fn write_jar(path: &Path, entries: &[(&str, &[u8])], method: CompressionMethod) {
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
    // The rule passes a --flagfile=, a one-shot run can add --trace-file= and --verify-crc, and no caller passes more. A
    // typo must fail the action. A run that skips the trace silently looks like a build that wrote no spans.
    let parse = |arguments: &[&str]| options::parse(arguments.iter().map(OsString::from));
    assert_eq!(
        parse(&["--flagfile=recipe.txt"]),
        Ok(Options {
            flag_file: "recipe.txt".into(),
            ..Options::default()
        })
    );
    assert_eq!(
        parse(&["--verify-crc", "--flagfile=recipe.txt", "--trace-file=out/a.jar.spans.json"]),
        Ok(Options {
            flag_file: "recipe.txt".into(),
            verify_crc: true,
            trace_file: Some("out/a.jar.spans.json".into()),
        })
    );
    let refused: [(&[&str], &str); 10] = [
        (&["--trace-file=out/a.jar.spans.json"], "--flagfile="),
        (&["--flagfile=recipe.txt", "--tracefile=x"], "not defined"),
        (&["--flagfile=recipe.txt", "x"], "unexpected argument"),
        // The Go `flag` package took these forms. No caller passes them.
        (&["--flagfile", "recipe.txt"], "value after `=`"),
        (&["-flagfile=recipe.txt"], "unsupported option -flagfile:"),
        (&["--flagfile=a.txt", "--flagfile=b.txt"], "twice"),
        (&["--flagfile=recipe.txt", "--verify-crc", "--verify-crc"], "twice"),
        (&["--flagfile=recipe.txt", "--verify-crc=true"], "takes no value"),
        (&["--flagfile=recipe.txt", "--trace-file="], "nonempty"),
        (&["--flagfile=recipe.txt", "--cpuprofile=cpu.pprof"], "--trace-file=<path>"),
    ];
    for (arguments, want) in refused {
        let error = parse(arguments).expect_err("a refused command line");
        assert!(error.contains(want), "{arguments:?}: the failure {error:?} must name {want:?}");
    }
    // A path of the recipe is UTF-8, and the trace destination goes through the same path rule.
    #[cfg(unix)]
    {
        use std::os::unix::ffi::OsStringExt;
        let arguments = [
            OsString::from("--flagfile=recipe.txt"),
            OsString::from_vec(b"--trace-file=out/\xff.spans.json".to_vec()),
        ];
        let error = options::parse(arguments).expect_err("a refused command line");
        assert!(error.contains("not valid UTF-8"), "the failure {error:?} must name the encoding");
    }

    // The process reports an option error with the usage and fails.
    let dir = tempfile::tempdir().expect("a scratch directory");
    let outcome = run_in(dir.path(), &["--flagfile=recipe.txt".into(), "--tracefile=x".into()]);
    assert_eq!(outcome.code, FAILURE);
    assert_eq!(
        outcome.stderr,
        format!("ERROR: flag provided but not defined: -tracefile\n{}\n", options::USAGE)
    );
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
    // The jarpack tests check the inventory itself. This test checks that the span shows the counters.
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

#[test]
fn natives_mode_refuses_a_trace_destination_inside_the_tree() {
    let dir = pack_one_native_jar();
    let before = snapshot(dir.path());
    let outcome = run_in(dir.path(), &[flag_file_argument(dir.path()), "--trace-file=out/native".into()]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(outcome.stderr.contains("native tree output"), "{}", outcome.stderr);
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
        "out/other.jar",
        "other.metadata.json",
        "library.jar",
        "resource.txt",
        "plugin.xml",
    ];
    for destination in destinations {
        for variant in ["relative", "absolute", "absolute dot", "absolute parent", "recipe"] {
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
            let mut arguments = vec![flag_file_argument(base)];
            let recipe_trace = if variant == "recipe" {
                trace_file.as_str()
            } else {
                arguments.push(format!("--trace-file={trace_file}"));
                "safe.spans.json"
            };
            write_recipe(
                base,
                &format!(
                    "output=out/example.jar\nmetadata-file=example.metadata.json\ntrace-file={recipe_trace}\n\
                     module=module.jar\noutput=out/other.jar\nmetadata-file=other.metadata.json\nlibrary=library.jar\n\
                     file=resource.txt=resource.txt\npatch=META-INF/plugin.xml=plugin.xml\n"
                ),
            );
            let before = snapshot(base);
            let outcome = run_in(base, &arguments);
            let case = format!("{destination}/{variant}");
            assert_eq!(outcome.code, FAILURE, "{case}: {}", outcome.stderr);
            // The Go packer cleaned an alias and then found the collision. The command line now refuses the alias
            // itself, as the recipe parser does.
            let refused = if variant.starts_with("absolute ") {
                outcome.stderr.contains("has a `.` or `..` component")
            } else {
                outcome.stderr.contains("trace destination") || outcome.stderr.contains("conflicting metadata destination")
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
    let dir = pack_one_jar("");
    let outcome = run_in(
        dir.path(),
        &[flag_file_argument(dir.path()), "--trace-file=out/example.jar.spans.json".into()],
    );
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);

    // The path is relative, so it resolves against the working directory, which is the exec root in a build. The span
    // file is beside the jar.
    let trace = read_trace(&dir.path().join("out/example.jar.spans.json"));
    assert_eq!(trace["processes"]["p1"]["serviceName"], "content-module-packer");
    assert_eq!(operation_names(&trace), ["pack content modules", "pack jar"]);
    let (root, jar) = (&spans(&trace)[0], &spans(&trace)[1]);
    assert!(root.get("references").is_none(), "the root span has a parent: {root}");
    assert_eq!(tags(root), owned(&[("jars", "1")]));
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
    let dir = pack_one_jar("");
    let outcome = run_in(
        dir.path(),
        &[flag_file_argument(dir.path()), "--trace-file=module.jar/spans.json".into()],
    );
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
fn the_command_line_wins_over_the_recipe() {
    // A flag file from `bazel aquery` holds the `trace-file=` of the action, which points into bazel-out. The option
    // lets the file run again with the trace in a safe location.
    let dir = pack_one_jar("trace-file=out/from-the-recipe.json\n");
    let outcome = run_in(
        dir.path(),
        &[flag_file_argument(dir.path()), "--trace-file=out/from-the-flag.json".into()],
    );
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    assert!(
        dir.path().join("out/from-the-flag.json").is_file(),
        "the destination of the option is missing"
    );
    assert!(
        !dir.path().join("out/from-the-recipe.json").exists(),
        "the run also wrote the destination of the recipe"
    );
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
fn a_one_shot_run_packs_each_group_and_reports_in_group_order() {
    // A whole tranche in one flag file. Each jar merges two copies of one module, so each jar reports one duplicate.
    let dir = pack_one_jar("");
    let base = dir.path();
    fs::copy(base.join("module.jar"), base.join("copy.jar")).expect("a second module jar");
    let names: Vec<String> = (0..24).map(|index| format!("jar{index:02}.jar")).collect();
    let recipe: String = names
        .iter()
        .map(|name| format!("output=out/{name}\nmodule=module.jar\nmodule=copy.jar\n"))
        .collect();
    write_recipe(base, &recipe);
    let outcome = run_in(base, &[flag_file_argument(base), "--trace-file=one-shot.spans.json".into()]);
    assert_eq!(outcome.code, 0, "{}", outcome.stderr);
    let want: String = names
        .iter()
        .map(|name| format!("{name}: 1 duplicate entry, first source wins: com/example/Packed.class\n"))
        .collect();
    assert_eq!(outcome.stderr, want);
    assert_eq!(file_names(&base.join("out")), names);

    // The rayon workers record into the tracer of the run, and each jar span is a child of the root.
    let trace = read_trace(&base.join("one-shot.spans.json"));
    let all = spans(&trace);
    assert_eq!(all.len(), names.len() + 1);
    assert_eq!(all[0]["operationName"], "pack content modules");
    assert_eq!(tags(&all[0]), owned(&[("jars", "24")]));
    let mut packed: Vec<String> = Vec::new();
    for span in &all[1..] {
        assert_eq!(span["operationName"], "pack jar");
        assert_eq!(span["references"][0]["spanID"], all[0]["spanID"], "{span}");
        packed.push(tags(span)["jar"].clone());
    }
    packed.sort();
    assert_eq!(packed, names);
}

#[test]
fn a_one_shot_run_fails_with_the_error_of_the_failed_group() {
    let dir = pack_one_jar("");
    let base = dir.path();
    let recipe: String = (0..8)
        .map(|index| {
            let source = if index == 5 { "missing.jar" } else { "module.jar" };
            format!("output=out/jar{index}.jar\nmodule={source}\n")
        })
        .collect();
    write_recipe(base, &recipe);
    let outcome = run_in(base, &[flag_file_argument(base)]);
    assert_eq!(outcome.code, FAILURE, "{}", outcome.stderr);
    assert!(
        outcome.stderr.starts_with("ERROR: ") && outcome.stderr.contains("missing.jar"),
        "{}",
        outcome.stderr
    );
}

#[test]
fn verify_crc_checks_the_data_of_each_source_entry() {
    // A STORED entry whose data no longer matches the CRC of its record. A build carries the CRC over, and a parity run
    // refuses it.
    let dir = tempfile::tempdir().expect("a scratch directory");
    let base = dir.path();
    let jar = base.join("module.jar");
    write_jar(&jar, &[("com/example/Packed.class", CLASS)], CompressionMethod::Stored);
    let mut content = fs::read(&jar).expect("the fixture jar");
    let offset = content
        .windows(CLASS.len())
        .position(|window| window == CLASS)
        .expect("the stored data");
    content[offset + CLASS.len() - 1] ^= 0xff;
    fs::write(&jar, content).expect("the damaged jar");
    write_recipe(base, "output=out/example.jar\nmodule=module.jar\n");

    let carried = run_in(base, &[flag_file_argument(base)]);
    assert_eq!(carried.code, 0, "{}", carried.stderr);
    let verified = run_in(base, &[flag_file_argument(base), "--verify-crc".into()]);
    assert_eq!(verified.code, FAILURE, "{}", verified.stderr);
    assert!(verified.stderr.contains("source CRC is"), "{}", verified.stderr);
}
