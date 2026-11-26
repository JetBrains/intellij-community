// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::io;

use crate::{answered, follow_stderr_colors};

#[test]
fn an_answer_is_some_value() {
    assert_eq!(answered(Ok(3)).unwrap(), Some(3));
}

#[test]
fn esc_or_ctrl_c_gives_none() {
    let answer: io::Result<usize> = Err(io::ErrorKind::Interrupted.into());
    assert_eq!(answered(answer).unwrap(), None);
}

#[test]
fn another_error_names_the_answer() {
    let answer: io::Result<usize> = Err(io::ErrorKind::NotConnected.into());
    let error = answered(answer).unwrap_err();
    assert_eq!(format!("{error:#}"), "cannot read the answer: not connected");
}

#[test]
fn the_colors_of_the_theme_follow_the_stderr_switch() {
    for enabled in [false, true] {
        console::set_colors_enabled_stderr(enabled);
        follow_stderr_colors();
        assert_eq!(console::colors_enabled(), enabled);
    }
}
