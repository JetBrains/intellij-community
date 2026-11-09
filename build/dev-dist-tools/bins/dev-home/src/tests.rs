use std::ffi::OsString;
use std::io::Write;

use testkit::{Run, run_tool};

/// Runs the tool as `main` does, with the answer in a buffer that the tests do not read.
fn run(args: &[&str]) -> Run {
    run_tool(|args: Vec<OsString>, errors: &mut dyn Write| crate::run(args, &mut Vec::new(), errors), args)
}

#[test]
fn a_command_line_without_a_verb_fails() {
    run(&["--spec=a.json"]).assert_error("ERROR: expected one verb, materialize or gc, got []\n");
}

#[test]
fn an_unknown_verb_fails() {
    run(&["build", "--spec=a.json"]).assert_error("ERROR: expected one verb, materialize or gc, got [\"build\"]\n");
}

#[test]
fn the_cmd_form_needs_an_output_file() {
    run(&["materialize", "--spec=a.json", "--format=cmd"]).assert_error("ERROR: --format=cmd needs --output\n");
}

#[test]
fn a_missing_spec_fails_with_its_path() {
    let run = run(&["materialize", "--spec=/no/such/idea.home.json", "--format=json"]);
    // The text of the OS error differs on Windows.
    run.assert_error("ERROR: cannot read /no/such/idea.home.json: ");
}
