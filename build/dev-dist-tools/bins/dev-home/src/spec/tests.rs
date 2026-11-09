use std::fs;

use testkit::write_file;

use crate::spec::read_spec;
use crate::test_support::Fixture;

fn spec_json(fixture: &Fixture) -> serde_json::Value {
    let spec = &fixture.spec;
    serde_json::json!({
        "version": 1,
        "target": spec.target,
        "root": spec.root,
        "fingerprint": spec.fingerprint,
        "homePrefix": spec.home_prefix,
        "layout": spec.layout,
        "files": spec.files,
        "trees": spec.trees,
        "launcher": spec.launcher,
        "vmOptions": spec.vm_options,
        "vmOptionsVariable": spec.vm_options_variable,
        "runtimeModuleRepository": spec.runtime_module_repository,
    })
}

fn write_spec(fixture: &Fixture, value: &serde_json::Value) -> std::path::PathBuf {
    let file = fixture.directory.path().join("idea.home.json");
    let _ = fs::remove_file(&file);
    write_file(&file, value.to_string());
    file
}

#[test]
fn a_spec_reads_back() {
    let fixture = Fixture::new("Contents");
    let file = write_spec(&fixture, &spec_json(&fixture));
    assert_eq!(read_spec(&file).unwrap(), fixture.spec);
}

#[test]
fn a_spec_of_another_version_fails() {
    let fixture = Fixture::new("");
    let mut value = spec_json(&fixture);
    value["version"] = 2.into();
    let error = format!("{:#}", read_spec(&write_spec(&fixture, &value)).unwrap_err());
    assert!(error.ends_with("the version is 2, not 1. Build the target again"), "{error}");
}

#[test]
fn a_relative_source_and_a_bad_variable_fail() {
    let fixture = Fixture::new("");
    let mut value = spec_json(&fixture);
    value["files"]["lib/app.jar"] = "lib/app.jar".into();
    let error = format!("{:#}", read_spec(&write_spec(&fixture, &value)).unwrap_err());
    assert!(
        error.ends_with("the source of lib/app.jar is not an absolute path: lib/app.jar"),
        "{error}"
    );

    let mut value = spec_json(&fixture);
    value["vmOptionsVariable"] = "idea vm".into();
    let error = format!("{:#}", read_spec(&write_spec(&fixture, &value)).unwrap_err());
    assert!(
        error.ends_with(r#"`vmOptionsVariable` is not a variable name: "idea vm""#),
        "{error}"
    );
}

#[test]
fn a_file_below_a_tree_has_its_source_in_the_tree() {
    let fixture = Fixture::new("");
    assert_eq!(
        fixture.spec.source("jbr/bin/java").unwrap(),
        fixture.spec.trees["jbr"].join("bin/java")
    );
    assert_eq!(fixture.spec.source("lib/app.jar").unwrap(), fixture.spec.files["lib/app.jar"]);
    let error = format!("{:#}", fixture.spec.source("plugins/a/lib/a.jar").unwrap_err());
    assert_eq!(error, "the home spec names no source for plugins/a/lib/a.jar");
}
