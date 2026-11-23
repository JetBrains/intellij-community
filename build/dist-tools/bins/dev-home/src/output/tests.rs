use std::path::{Path, PathBuf};

use crate::generation::Materialized;
use crate::output::{Format, render, sh_quote};
use crate::spec::LaunchSpec;
use crate::test_support::launch;

fn fixture_launch() -> LaunchSpec {
    launch(Path::new("/w"))
}

/// A generation below `root`. Each path is one string, so it has `/` on every OS, as `join` would not on Windows.
fn materialized(root: &str) -> Materialized {
    Materialized {
        launcher: PathBuf::from(format!("{root}/g/v5-a-flat/bin/idea")),
        vm_options_file: PathBuf::from(format!("{root}/g/v5-a-flat/vmoptions-00")),
        home: PathBuf::from(format!("{root}/g/v5-a-flat")),
        vm_options_variable: "IDEA_VM_OPTIONS".to_owned(),
        built: false,
    }
}

#[test]
fn the_json_form_names_the_home_the_launcher_the_vm_options_and_the_launch() {
    let text = render(&materialized("/r"), &fixture_launch(), Format::Json).unwrap();
    let value: serde_json::Value = serde_json::from_str(&text).unwrap();
    assert_eq!(value["home"], "/r/g/v5-a-flat");
    assert_eq!(value["launcher"], "/r/g/v5-a-flat/bin/idea");
    assert_eq!(value["vmOptionsFile"], "/r/g/v5-a-flat/vmoptions-00");
    assert_eq!(value["vmOptionsVariable"], "IDEA_VM_OPTIONS");
    assert_eq!(value["programArguments"], serde_json::json!(["--nosplash"]));
    let launch = fixture_launch();
    assert_eq!(value["workingDirectory"], launch.working_directory.display().to_string());
    assert_eq!(value["env"], serde_json::json!(launch.env));
    assert_eq!(value["unsetEnv"], serde_json::json!(["RUNFILES_MANIFEST_FILE"]));
}

#[test]
fn the_sh_form_quotes_each_value() {
    let text = render(&materialized("/my dir/it's"), &fixture_launch(), Format::Sh).unwrap();
    assert_eq!(
        text,
        "DEV_HOME_LAUNCHER='/my dir/it'\\''s/g/v5-a-flat/bin/idea'\nexport IDEA_VM_OPTIONS='/my dir/it'\\''s/g/v5-a-flat/vmoptions-00'\n"
    );
    assert_eq!(sh_quote("a'b"), r"'a'\''b'");
}

#[test]
fn the_cmd_form_sets_two_variables_and_refuses_a_percent() {
    let text = render(&materialized("/r"), &fixture_launch(), Format::Cmd).unwrap();
    assert_eq!(
        text,
        "set \"DEV_HOME_LAUNCHER=/r/g/v5-a-flat/bin/idea\"\r\nset \"IDEA_VM_OPTIONS=/r/g/v5-a-flat/vmoptions-00\"\r\n"
    );
    let error = format!("{:#}", render(&materialized("/r%TEMP%"), &fixture_launch(), Format::Cmd).unwrap_err());
    assert!(error.contains("which a `.cmd` file cannot hold"), "{error}");
}

#[test]
fn an_unknown_format_fails() {
    let error = format!("{:#}", Format::parse("xml").unwrap_err());
    assert_eq!(error, r#"--format takes json, sh or cmd, got "xml""#);
}
