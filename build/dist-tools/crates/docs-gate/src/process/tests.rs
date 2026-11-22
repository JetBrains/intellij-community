use super::{Finished, run};
use std::process::Command;
use std::time::Duration;

#[test]
fn keeps_the_output_and_the_exit_code() {
    let finished = run(
        Command::new("sh").args(["-c", "echo out; echo err >&2; exit 3"]),
        Duration::from_secs(10),
    )
    .expect("sh runs");

    let Finished::Exited { status, stdout, stderr } = finished else {
        panic!("sh exits before the deadline: {finished:?}");
    };
    assert_eq!(status.code(), Some(3));
    assert_eq!(stdout, "out\n");
    assert_eq!(stderr, "err\n");
}

#[test]
fn kills_a_process_past_its_deadline() {
    let finished = run(
        Command::new("sh").args(["-c", "echo started >&2; exec sleep 30"]),
        Duration::from_millis(300),
    )
    .expect("sh runs");

    let Finished::TimedOut { stderr } = finished else {
        panic!("sleep runs past the deadline: {finished:?}");
    };
    assert_eq!(stderr, "started\n");
}

#[test]
fn the_output_of_a_process_is_stdout_then_stderr_trimmed() {
    assert_eq!(super::output_of("out\n", "err\n"), "out\n\nerr");
    assert_eq!(super::output_of("", "x".repeat(5000).as_str()).len(), 4000);
}
