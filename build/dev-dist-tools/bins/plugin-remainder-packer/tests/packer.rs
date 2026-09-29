//! The tests of the Go `pluginpack_test` target that ran the packer binary. They are
//! `TestRawTreeIsAbsentUntilGoExecution`, `TestBinaryOutputAliasesPreserveContractFiles`, and the projection half of
//! `TestGoPlanDerivationMatchesKotlinPreparer`. The fourth is `TestRelativeDirectoryRootsResolveThroughWorkingDirectoryLinks`.
//! The Go test changed the working directory of its process. The port starts the binary in that directory instead.
//!
//! The tests read the path of the packer binary from `CARGO_BIN_EXE_plugin-remainder-packer` at run time. Where the
//! variable is absent, each test skips with a message.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

fn packer() -> Option<PathBuf> {
    if let Some(path) = std::env::var_os("CARGO_BIN_EXE_plugin-remainder-packer") {
        // Bazel names the binary relative to the start directory, and each run starts the binary in another directory.
        Some(std::path::absolute(path).unwrap())
    } else {
        eprintln!("CARGO_BIN_EXE_plugin-remainder-packer is not set; run `cargo test` to include the packer binary");
        None
    }
}

fn run(packer: &Path, arguments: &[String], directory: &Path) -> Output {
    Command::new(packer).args(arguments).current_dir(directory).output().unwrap()
}

fn write_file(file: &Path, data: &[u8]) {
    fs::create_dir_all(file.parent().unwrap()).unwrap();
    fs::write(file, data).unwrap();
}

fn symlink(target: impl AsRef<Path>, link: &Path) {
    let target_is_directory = link.parent().unwrap().join(target.as_ref()).is_dir();
    fscopy::symlink(target.as_ref(), link, target_is_directory).unwrap();
}

#[cfg_attr(not(unix), expect(clippy::missing_const_for_fn, reason = "the Unix path sets the mode"))]
fn chmod(path: &Path, mode: u32) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(mode)).unwrap();
    }
    #[cfg(not(unix))]
    let _ = (path, mode);
}

fn exists(path: &Path) -> bool {
    fs::symlink_metadata(path).is_ok()
}

/// Every entry below `root` in path order: the path, the kind, the mode, and the bytes or the link target.
fn snapshot(root: &Path) -> Vec<String> {
    let mut record = Vec::new();
    for entry in walkdir::WalkDir::new(root).min_depth(1).sort_by_file_name() {
        let entry = entry.unwrap();
        let relative = entry.path().strip_prefix(root).unwrap().display().to_string();
        let metadata = entry.metadata().unwrap();
        let mode = filemeta::permissions(&metadata);
        if metadata.is_file() {
            record.push(format!("{relative} file {mode:o} {:?}", fs::read(entry.path()).unwrap()));
        } else if metadata.is_dir() {
            record.push(format!("{relative} directory {mode:o}"));
        } else {
            record.push(format!("{relative} link {}", fs::read_link(entry.path()).unwrap().display()));
        }
    }
    record
}

fn jar(entries: &[(&str, &str)]) -> Vec<u8> {
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    for (name, content) in entries {
        writer.start_file(*name, zip::write::SimpleFileOptions::default()).unwrap();
        writer.write_all(content.as_bytes()).unwrap();
    }
    writer.finish().unwrap().into_inner()
}

/// Writes the plan file, the input catalogue and the classpath descriptor of one plugin under `contracts`. The plan
/// copies `source` as the tree `resources`, or as the file `lib/raw.jar`. The result is the argument list of the packer
/// without its four outputs.
fn projection_contracts(contracts: &Path, tree: bool, source: &Path) -> Vec<String> {
    let (version, kind, asset) = if tree {
        (
            2,
            "directory",
            r#"{"destination": "resources", "inputs": ["raw"], "kind": "tree", "classPath": false}"#,
        )
    } else {
        (1, "file", r#"{"destination": "lib/raw.jar", "inputs": ["raw"]}"#)
    };
    let plan = format!(
        r#"{{"version": {version}, "plugin": "test.plugin", "variant": "default", "layoutSignature": "raw-v{version}", "assets": [{asset}]}}"#
    );
    let catalogue = serde_json::json!({"version": 1, "artifacts": [{"id": "raw", "kind": kind, "root": source}]});
    write_file(&contracts.join("plan.json"), plan.as_bytes());
    write_file(&contracts.join("catalogue.json"), &serde_json::to_vec(&catalogue).unwrap());
    write_file(
        &contracts.join("descriptor.xml"),
        b"<idea-plugin><id>test.plugin</id></idea-plugin>",
    );
    vec![
        format!("--projection={}", contracts.join("plan.json").display()),
        format!("--input-catalogue={}", contracts.join("catalogue.json").display()),
        format!("--classpath-descriptor={}", contracts.join("descriptor.xml").display()),
        "--plugin-directory=plugins/test".to_owned(),
        format!("--execution-version={version}"),
    ]
}

fn outputs(output: &str, inventory: &str, root: &Path) -> Vec<String> {
    vec![
        format!("--output-dir={output}"),
        format!("--inventory={inventory}"),
        format!("--assets={}", root.join("assets.json").display()),
        format!("--classpath={}", root.join("plugin-classpath.txt").display()),
    ]
}

/// Runs the packer on a plan whose raw tree is missing. The packer must refuse the plan before any write, and copy the
/// tree with its bytes, modes, directories and links once it exists.
#[test]
fn raw_tree_is_absent_until_execution() {
    let Some(packer) = packer() else { return };
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("raw-tree");
    let mut arguments = projection_contracts(&root.path().join("contracts"), true, &source);
    arguments.extend(outputs("plugin", "inventory.json", root.path()));
    let output = run(&packer, &arguments, root.path());
    assert!(!output.status.success(), "the packer accepted the missing raw tree");
    for name in ["plugin", "inventory.json", "assets.json", "plugin-classpath.txt"] {
        assert!(
            !exists(&root.path().join(name)),
            "the packer wrote {name} before the raw tree validation"
        );
    }
    write_file(&source.join("file"), b"prepared later");
    chmod(&source.join("file"), 0o751);
    fs::create_dir(source.join("empty")).unwrap();
    chmod(&source.join("empty"), 0o710);
    #[cfg(unix)]
    std::os::unix::fs::symlink("./file", source.join("link")).unwrap();
    let before = filemeta::inventory(&source).unwrap();
    let output = run(&packer, &arguments, root.path());
    assert!(
        output.status.success(),
        "the packer rejected the raw tree: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert_eq!(filemeta::inventory(&source).unwrap(), before, "the packer changed the raw tree");
    assert_eq!(
        filemeta::inventory(&root.path().join("plugin/resources")).unwrap(),
        before,
        "the bytes, modes, directories or links of the raw tree differ"
    );
}

#[test]
fn binary_output_aliases_preserve_contract_files() {
    let Some(packer) = packer() else { return };
    for tree in [false, true] {
        for (name, alias_name) in [("Contracts", "contracts"), ("Caf\u{e9}", "Cafe\u{301}")] {
            for document in ["plan.json", "catalogue.json", "descriptor.xml"] {
                for destination in ["payload", "inventory"] {
                    let root = tempfile::tempdir().unwrap();
                    let mut source = root.path().join("source");
                    write_file(&source.join("file"), b"immutable source");
                    if !tree {
                        source = source.join("file");
                    }
                    let contracts = root.path().join(name);
                    let alias = root.path().join(alias_name);
                    let mut arguments = projection_contracts(&contracts, tree, &source);
                    if !exists(&alias) {
                        eprintln!("The file system distinguishes {name:?} and {alias_name:?}");
                        continue;
                    }
                    let (mut output, mut inventory) = (root.path().join("output"), root.path().join("inventory.json"));
                    if destination == "payload" {
                        output = alias.join(document);
                    } else {
                        inventory = alias.join(document);
                    }
                    let before = snapshot(root.path());
                    arguments.extend(outputs(
                        &output.display().to_string(),
                        &inventory.display().to_string(),
                        root.path(),
                    ));
                    let result = run(&packer, &arguments, root.path());
                    let message = String::from_utf8_lossy(&result.stderr);
                    let want = if destination == "payload" {
                        "not a real directory"
                    } else {
                        "inventory must not exist"
                    };
                    assert!(
                        !result.status.success() && message.contains(want),
                        "tree={tree}/{name}/{document}/{destination}: {message}"
                    );
                    assert_eq!(snapshot(root.path()), before, "a contract or the source changed");
                }
            }
        }
    }
}

/// The packer writes every output below a directory whose path is longer than `MAX_PATH` of Windows, which is 260
/// characters. A Bazel output path on Windows can be that long.
#[test]
fn outputs_pack_past_max_path() {
    let Some(packer) = packer() else { return };
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("source");
    write_file(&source.join("file"), b"long path");
    let mut arguments = projection_contracts(&root.path().join("contracts"), true, &source);
    let mut package = root.path().to_path_buf();
    for index in 0..6 {
        package.push(format!("{index}-intellij.air.integrationTests.bridge.plugin"));
    }
    let output = package.join("plugin_remainder.plugin");
    let inventory = package.join("plugin_remainder.file-metadata.json");
    assert!(
        inventory.as_os_str().len() > 260,
        "the test path is too short: {}",
        inventory.display()
    );
    arguments.extend(outputs(&output.display().to_string(), &inventory.display().to_string(), &package));
    let result = run(&packer, &arguments, root.path());
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
    assert_eq!(fs::read(output.join("resources/file")).unwrap(), b"long path");
    assert!(exists(&inventory), "the packer wrote no inventory");
    assert!(exists(&package.join("assets.json")), "the packer wrote no asset rows");
}

/// A relative catalogue root resolves against the working directory, also when the working directory is a link.
#[test]
fn relative_directory_roots_resolve_through_working_directory_links() {
    let Some(packer) = packer() else { return };
    let root = tempfile::tempdir().unwrap();
    let physical = root.path().join("physical");
    write_file(&physical.join("entries/input.txt"), b"relative input");
    let alias = root.path().join("alias");
    symlink(&physical, &alias);
    let mut arguments = projection_contracts(&root.path().join("contracts"), true, Path::new("entries"));
    let output = root.path().join("output");
    arguments.extend(outputs(
        &output.display().to_string(),
        &root.path().join("inventory.json").display().to_string(),
        root.path(),
    ));
    let result = Command::new(&packer)
        .args(&arguments)
        .current_dir(&alias)
        .env("PWD", &alias)
        .output()
        .unwrap();
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
    assert_eq!(fs::read(output.join("resources/input.txt")).unwrap(), b"relative input");
}

/// The projection mode of the packer writes the same directory, inventory, asset rows and classpath record as the
/// in-process derivation of the same plan file.
#[test]
fn projection_mode_matches_the_in_process_derivation() {
    let Some(packer) = packer() else { return };
    let root = tempfile::tempdir().unwrap();
    let inputs = root.path().join("inputs");
    write_file(&inputs.join("raw/keep/Service.txt"), b"copied into the jar");
    write_file(
        &inputs.join("demo.extra.jar"),
        &jar(&[
            ("demo/extra/Main.class", "class of demo.extra"),
            ("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nModule: demo.extra\r\n\r\n"),
        ]),
    );
    let plan = r#"{"version": 1, "plugin": "filtered", "variant": "", "layoutSignature": "signature", "assets": [
      {"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered:output", "kind": "prepared", "filter": "prepared"}, {"input": "demo.extra", "kind": "module", "filter": "module-v1"}], "writer": {"manifest": "keep", "mergeEntities": true}}}],
      "preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered:output"], "modelSignature": "x"}],
      "operations": [{"id": "filter", "kind": "layout-assets", "inputs": [{"artifact": "raw"}], "output": "filtered:output", "manifest": "keep",
        "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0]}]}}]}"#;
    let catalogue_json = serde_json::json!({"version": 1, "artifacts": [
        {"id": "raw", "kind": "directory", "root": inputs.join("raw")},
        {"id": "demo.extra", "kind": "file", "root": inputs.join("demo.extra.jar")},
    ]});
    let descriptor = b"<idea-plugin><id>filtered</id><version>1</version></idea-plugin>";
    write_file(&root.path().join("plan.json"), plan.as_bytes());
    write_file(&root.path().join("catalogue.json"), &serde_json::to_vec(&catalogue_json).unwrap());
    write_file(&root.path().join("descriptor.xml"), descriptor);

    let file = planfile::read(&root.path().join("plan.json")).unwrap();
    let catalogue: planfile::contract::Catalogue = planfile::json::read(&root.path().join("catalogue.json")).unwrap();
    let derivation = planfile::derive(&file, &catalogue, "plugins/filtered", descriptor, 1, &[], &[]).unwrap();
    let in_process = root.path().join("in-process");
    pluginpack::plan(&derivation.recipe, &derivation.catalogue)
        .unwrap()
        .write(&in_process.join("plugin"), &in_process.join("inventory.json"))
        .unwrap();

    let packed = root.path().join("packed");
    let arguments = vec![
        format!("--projection={}", root.path().join("plan.json").display()),
        format!("--input-catalogue={}", root.path().join("catalogue.json").display()),
        format!("--classpath-descriptor={}", root.path().join("descriptor.xml").display()),
        "--plugin-directory=plugins/filtered".to_owned(),
        "--execution-version=1".to_owned(),
        format!("--output-dir={}", packed.join("plugin").display()),
        format!("--inventory={}", packed.join("inventory.json").display()),
        format!("--assets={}", packed.join("assets.json").display()),
        format!("--classpath={}", packed.join("plugin-classpath.txt").display()),
    ];
    let result = run(&packer, &arguments, root.path());
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
    assert_eq!(
        String::from_utf8(result.stdout).unwrap(),
        "Packed the remainder for filtered from its plan file\n"
    );
    assert_eq!(
        snapshot(&packed.join("plugin")),
        snapshot(&in_process.join("plugin")),
        "the plugin directory differs"
    );
    assert_eq!(
        filemeta::read(&packed.join("inventory.json")).unwrap(),
        filemeta::read(&in_process.join("inventory.json")).unwrap(),
        "the inventory differs"
    );
    assert_eq!(
        fs::read(packed.join("assets.json")).unwrap(),
        serde_json::to_vec(&derivation.assets).unwrap()
    );
    assert_eq!(fs::read(packed.join("plugin-classpath.txt")).unwrap(), derivation.class_path);
}
