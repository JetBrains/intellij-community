//! The port of `plugin-remainder-packer/main_test.go`.

use std::ffi::OsString;
use std::fs;
use std::io::Write;
use std::path::Path;

use super::run;

fn arguments(values: &[&str]) -> Vec<OsString> {
    values.iter().map(OsString::from).collect()
}

fn run_captured(values: Vec<OsString>) -> (u8, String, String) {
    let mut output = Vec::new();
    let mut errors = Vec::new();
    let code = run(values, &mut output, &mut errors);
    (code, String::from_utf8(output).unwrap(), String::from_utf8(errors).unwrap())
}

const ALL: [&str; 9] = [
    "--projection=plan.json",
    "--input-catalogue=catalogue.json",
    "--classpath-descriptor=descriptor.xml",
    "--plugin-directory=plugins/x",
    "--execution-version=1",
    "--output-dir=out",
    "--inventory=inventory.json",
    "--assets=assets.json",
    "--classpath=classpath.txt",
];

/// Refuses an unknown option, a malformed option, a repeated option, a missing option, an empty independent module,
/// and a version outside the range. The deleted recipe options are unknown options.
#[test]
fn arguments_are_refused_with_the_usage_code() {
    let with = |extra: &'static str| {
        let mut values = ALL.to_vec();
        values.push(extra);
        values
    };
    let mut version_four = ALL.to_vec();
    version_four[4] = "--execution-version=4";
    let cases: Vec<Vec<&str>> = vec![
        vec![],
        vec!["--unknown=value"],
        vec!["--projection"],
        vec!["--projection="],
        vec!["--projection=one", "--projection=two"],
        vec!["--projection=plan.json"],
        vec!["--independent-module"],
        vec!["--independent-module="],
        vec![
            "--recipe=recipe.json",
            "--catalogue=catalogue.json",
            "--output-dir=out",
            "--inventory=inventory.json",
        ],
        with("--catalogue=c.json"),
        with("--recipe=recipe.json"),
        with("positional"),
        vec!["--projection", "plan.json"],
        version_four,
    ];
    for case in cases {
        let (code, output, errors) = run_captured(arguments(&case));
        assert!(
            code == 2 && !errors.is_empty() && output.is_empty(),
            "{case:?}: code={code}, output={output:?}, errors={errors:?}"
        );
    }
    let (code, _, errors) = run_captured(arguments(&["--recipe=recipe.json"]));
    assert_eq!(code, 2);
    assert!(errors.contains(r#"unknown option "--recipe""#), "{errors}");
}

/// A plan file with one remainder jar, one module jar that the chain reuses, and one raw file copy. The chain names the
/// reused module with `--independent-module`, and the plan states it as a module asset only.
const PROJECTION_PLAN: &str = r#"{
  "version": 1, "plugin": "example", "variant": "", "layoutSignature": "signature",
  "assets": [
    {"destination": "lib/example.jar", "recipe": {"sources": [{"input": "example.main", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}},
    {"module": "example.content"},
    {"destination": "bin/tool", "inputs": ["tool"], "mode": 493, "classPath": false}
  ]
}"#;

fn main_jar() -> Vec<u8> {
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    writer
        .start_file("com/example/Main.class", zip::write::SimpleFileOptions::default())
        .unwrap();
    writer.write_all(b"class").unwrap();
    writer.finish().unwrap().into_inner()
}

fn write_projection_fixture(root: &Path, plan: &str) -> Vec<String> {
    let jar = root.join("main.jar");
    let catalogue = serde_json::json!({"version": 1, "artifacts": [
        {"id": "example.main", "kind": "file", "root": jar},
        {"id": "tool", "kind": "file", "root": root.join("tool")},
    ]});
    fs::write(&jar, main_jar()).unwrap();
    fs::write(root.join("tool"), b"tool").unwrap();
    fs::write(root.join("plan.json"), plan).unwrap();
    fs::write(root.join("catalogue.json"), serde_json::to_vec(&catalogue).unwrap()).unwrap();
    fs::write(root.join("descriptor.xml"), b"<idea-plugin/>").unwrap();
    let path = |name: &str| root.join(name).display().to_string();
    vec![
        format!("--projection={}", path("plan.json")),
        format!("--input-catalogue={}", path("catalogue.json")),
        format!("--classpath-descriptor={}", path("descriptor.xml")),
        "--plugin-directory=plugins/example".to_owned(),
        "--execution-version=1".to_owned(),
        format!("--output-dir={}", path("payload")),
        format!("--inventory={}", path("inventory.json")),
        format!("--assets={}", path("assets.json")),
        format!("--classpath={}", path("plugin-classpath.txt")),
        "--independent-module=example.content".to_owned(),
    ]
}

fn mode_of(path: &Path) -> u32 {
    filemeta::permissions(&fs::metadata(path).unwrap())
}

#[test]
fn projection_run_writes_the_plugin_the_assets_and_the_class_path() {
    let root = tempfile::tempdir().unwrap();
    let values = write_projection_fixture(root.path(), PROJECTION_PLAN);
    let (code, output, errors) = run_captured(values.into_iter().map(OsString::from).collect());
    assert!(
        code == 0 && !output.is_empty() && errors.is_empty(),
        "code={code}, output={output:?}, errors={errors:?}"
    );
    assert!(root.path().join("payload/lib/example.jar").is_file());
    assert_eq!(mode_of(&root.path().join("payload/bin/tool")), 0o755);
    assert!(
        fs::symlink_metadata(root.path().join("payload/lib/modules/example.content.jar")).is_err(),
        "the independent jar entered the remainder"
    );
    let assets = fs::read_to_string(root.path().join("assets.json")).unwrap();
    assert_eq!(
        assets,
        concat!(
            r#"[{"destination":"lib/example.jar","producer":"remainder"},"#,
            r#"{"destination":"lib/modules/example.content.jar","producer":"independent","artifact":"example.content"},"#,
            r#"{"destination":"bin/tool","producer":"remainder","classPath":false}]"#,
        )
    );
    let classpath = fs::read(root.path().join("plugin-classpath.txt")).unwrap();
    let expected = planfile::classpath::record("example", b"<idea-plugin/>", &["lib/example.jar"]).unwrap();
    assert_eq!(classpath, expected);
    #[cfg(unix)]
    for name in ["assets.json", "plugin-classpath.txt"] {
        assert_eq!(mode_of(&root.path().join(name)), 0o644, "{name}");
    }
}

/// Also refuses a reused module that the plan has no plain module jar for, and a plan that still states
/// `reusableArtifacts`. Every refusal happens before any write.
#[test]
fn projection_run_refuses_a_kotlin_preparation_and_a_stale_version() {
    let tail = "  ]\n}";
    assert!(PROJECTION_PLAN.ends_with(tail));
    let kotlin_plan = PROJECTION_PLAN
        .replacen(
            tail,
            r#"  ],
  "preparations": [{"id": "native", "inputs": ["tool"], "outputs": ["native:output"], "modelSignature": "x"}],
  "operations": [{"id": "native", "kind": "library-layout-patches", "inputs": [{"artifact": "tool"}], "output": "native:output", "manifest": "keep", "libraryLayout": {"any": 1}}]
}"#,
            1,
        )
        .replacen(r#""inputs": ["tool"], "mode": 493"#, r#""inputs": ["native:output"], "mode": 493"#, 1);
    let stale_plan = PROJECTION_PLAN.replacen(
        tail,
        r#"  ],
  "reusableArtifacts": [{"label": "//example:content.jar", "module": "example.content"}]
}"#,
        1,
    );
    for (name, plan, version, module, message) in [
        (
            "a Kotlin operation kind",
            kotlin_plan.as_str(),
            "1",
            "example.content",
            "libraryLayout",
        ),
        (
            "a stale version",
            PROJECTION_PLAN,
            "2",
            "example.content",
            "stale execution version",
        ),
        (
            "a module without a plain jar",
            PROJECTION_PLAN,
            "1",
            "example.other",
            r#"independent module "example.other" matches no module jar asset"#,
        ),
        (
            "a plan with reusableArtifacts",
            stale_plan.as_str(),
            "1",
            "example.content",
            "reusableArtifacts",
        ),
    ] {
        let root = tempfile::tempdir().unwrap();
        let mut values = write_projection_fixture(root.path(), plan);
        values[4] = format!("--execution-version={version}");
        let last = values.len() - 1;
        values[last] = format!("--independent-module={module}");
        let (code, output, errors) = run_captured(values.into_iter().map(OsString::from).collect());
        assert!(
            code == 1 && errors.contains(message) && output.is_empty(),
            "{name}: code={code}, output={output:?}, errors={errors:?}"
        );
        for file in ["payload", "inventory.json", "assets.json", "plugin-classpath.txt"] {
            assert!(
                fs::symlink_metadata(root.path().join(file)).is_err(),
                "{name}: a failed run wrote {file}"
            );
        }
    }
}

/// `--refused-module` names a content module that the product mode refuses. The asset whose every module is refused is
/// omitted: the packer writes no file for it and states no row and no classpath jar. A reused jar of a refused module
/// leaves the rows too. A refused module that no asset merges is a stale declaration and fails before any write.
#[test]
fn projection_run_omits_the_assets_of_a_refused_module() {
    let root = tempfile::tempdir().unwrap();
    let mut values = write_projection_fixture(root.path(), PROJECTION_PLAN);
    values.push("--refused-module=example.main".to_owned());
    values.push("--refused-module=example.content".to_owned());
    let (code, output, errors) = run_captured(values.into_iter().map(OsString::from).collect());
    assert!(
        code == 0 && !output.is_empty() && errors.is_empty(),
        "code={code}, output={output:?}, errors={errors:?}"
    );
    assert!(
        fs::symlink_metadata(root.path().join("payload/lib/example.jar")).is_err(),
        "the omitted jar entered the remainder"
    );
    assert_eq!(mode_of(&root.path().join("payload/bin/tool")), 0o755);
    let assets = fs::read_to_string(root.path().join("assets.json")).unwrap();
    assert_eq!(assets, r#"[{"destination":"bin/tool","producer":"remainder","classPath":false}]"#);
    let classpath = fs::read(root.path().join("plugin-classpath.txt")).unwrap();
    let expected = planfile::classpath::record::<&str>("example", b"<idea-plugin/>", &[]).unwrap();
    assert_eq!(classpath, expected);

    for (name, extra, message) in [
        (
            "an unmatched module",
            "--refused-module=example.other",
            r#"refused module "example.other" matches no asset of the plan"#,
        ),
        (
            "an empty module",
            "--refused-module=",
            "expected a nonempty --refused-module=value option",
        ),
    ] {
        let root = tempfile::tempdir().unwrap();
        let mut values = write_projection_fixture(root.path(), PROJECTION_PLAN);
        values.push(extra.to_owned());
        let (code, output, errors) = run_captured(values.into_iter().map(OsString::from).collect());
        assert!(
            code != 0 && errors.contains(message) && output.is_empty(),
            "{name}: code={code}, output={output:?}, errors={errors:?}"
        );
        for file in ["payload", "inventory.json", "assets.json", "plugin-classpath.txt"] {
            assert!(
                fs::symlink_metadata(root.path().join(file)).is_err(),
                "{name}: a failed run wrote {file}"
            );
        }
    }
}

/// Writes one zip with the given deflated entries.
fn write_deflated_zip(file: &Path, entries: &[(&str, &str)]) {
    let mut writer = zip::ZipWriter::new(fs::File::create(file).unwrap());
    let options = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    for (name, content) in entries {
        writer.start_file(*name, options).unwrap();
        writer.write_all(content.as_bytes()).unwrap();
    }
    writer.finish().unwrap();
}

/// The gzip resources mode writes one gzip member per XML entry under the output directory, and prints nothing. The
/// member holds the deflate stream of the source entry between the gzip header and the trailer.
#[test]
fn gzip_resources_mode_writes_one_member_per_xml_entry() {
    let root = tempfile::tempdir().unwrap();
    let archive = root.path().join("postgres-1.256.jar");
    write_deflated_zip(&archive, &[("com/db/postgres.minicat.xml", &"<model/>".repeat(1000))]);
    let output = root.path().join("out");
    let (code, printed, errors) = run_captured(vec![
        OsString::from("gzip-resources"),
        OsString::from(format!("--output-dir={}", output.display())),
        archive.clone().into_os_string(),
    ]);
    assert_eq!((code, printed.as_str(), errors.as_str()), (0, "", ""));
    let member = fs::read(output.join("com/db/postgres.minicat.xml.gzip")).unwrap();
    let mut source = zip::ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
    let mut deflated = Vec::new();
    std::io::Read::read_to_end(&mut source.by_index_raw(0).unwrap(), &mut deflated).unwrap();
    assert_eq!(member[..2], [0x1f, 0x8b], "the gzip magic");
    assert_eq!(member[10..member.len() - 8], deflated[..], "the member is not the source stream");
}

/// The gzip resources mode refuses a missing output, a missing archive, an unknown option and a repeated output with the
/// usage code, and a file that is not XML with the failure code.
#[test]
fn gzip_resources_mode_refuses_bad_arguments_and_non_xml_entries() {
    let root = tempfile::tempdir().unwrap();
    let output = format!("--output-dir={}", root.path().join("out").display());
    for case in [
        vec!["gzip-resources"],
        vec!["gzip-resources", "a.jar"],
        vec!["gzip-resources", output.as_str()],
        vec!["gzip-resources", output.as_str(), "--unknown=x", "a.jar"],
        vec!["gzip-resources", output.as_str(), output.as_str(), "a.jar"],
    ] {
        let (code, _, errors) = run_captured(arguments(&case));
        assert!(code == 2 && !errors.is_empty(), "{case:?}: code={code}, errors={errors:?}");
    }
    let archive = root.path().join("notes.jar");
    write_deflated_zip(&archive, &[("notes.txt", "text")]);
    let (code, _, errors) = run_captured(vec![
        OsString::from("gzip-resources"),
        OsString::from(output),
        archive.into_os_string(),
    ]);
    assert_eq!(code, 1);
    assert!(errors.contains(r#"unexpected file "notes.txt""#), "{errors}");
}
