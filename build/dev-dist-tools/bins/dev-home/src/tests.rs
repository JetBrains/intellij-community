use testkit::run_tool;

use crate::run;

#[test]
fn a_command_line_without_a_verb_fails() {
    run_tool(run, &["--spec=a.json"]).assert_error("ERROR: expected one verb, materialize or gc, got []\n");
}

#[test]
fn an_unknown_verb_fails() {
    run_tool(run, &["build", "--spec=a.json"]).assert_error("ERROR: expected one verb, materialize or gc, got [\"build\"]\n");
}

#[test]
fn the_cmd_form_needs_an_output_file() {
    run_tool(run, &["materialize", "--spec=a.json", "--format=cmd"]).assert_error("ERROR: --format=cmd needs --output\n");
}

#[test]
fn a_missing_spec_fails_with_its_path() {
    let run = run_tool(run, &["materialize", "--spec=/no/such/idea.home.json", "--format=json"]);
    run.assert_error("ERROR: cannot read /no/such/idea.home.json: No such file or directory (os error 2)\n");
}
