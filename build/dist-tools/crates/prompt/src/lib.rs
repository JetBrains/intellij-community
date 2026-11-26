// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The prompts of a dialog in a terminal: a menu, a yes or no question, a line of text, the message lines and a wait.
//! `cliclack` draws them on stderr with its stock theme, on macOS, Linux and Windows. Esc and Ctrl+C at a prompt give
//! `None`. On macOS and Linux the terminal has no echo and no signal keys while a prompt runs.

use std::io;

use anyhow::{Context as _, Result};

/// The kind of a message.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tone {
    /// A fact, such as a wait.
    Info,
    /// A result, such as a replaced home.
    Success,
    /// A fact that asks for attention, such as a release home.
    Warning,
    /// A failure.
    Error,
    /// A line below an earlier message, such as one error line of a build.
    Detail,
}

/// One option of a menu.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Item {
    /// The row of the menu. It stays below the question after the answer.
    pub label: String,
    /// The text after the row under the cursor. An empty hint shows nothing.
    pub hint: String,
}

/// Asks for one of `items`, with the cursor on `default` first. Typing filters the rows. Returns the index of the
/// option, or `None` for Esc or Ctrl+C.
pub fn select(title: &str, items: Vec<Item>, default: usize) -> Result<Option<usize>> {
    let _quiet = start_prompt();
    let mut select = cliclack::select(title).filter_mode().max_rows(MAX_ROWS);
    for (index, item) in items.into_iter().enumerate() {
        select = select.item(index, item.label, item.hint);
    }
    answered(select.initial_value(default).interact())
}

/// Asks a yes or no question as a menu of the rows "Yes" and "No", with the cursor on `default` first. The menu has no
/// filter, so only Enter answers, and a typed `y` never takes the Enter of the next question. Returns `None` for Esc or
/// Ctrl+C.
pub fn confirm(question: &str, default: bool) -> Result<Option<bool>> {
    let _quiet = start_prompt();
    let mut menu = cliclack::select(question)
        .item(true, "Yes", "")
        .item(false, "No", "")
        .initial_value(default);
    answered(menu.interact())
}

/// Asks for one line of text, such as a path. An empty line is refused at the prompt. Returns `None` for Esc or
/// Ctrl+C.
pub fn text(prompt: &str) -> Result<Option<String>> {
    let _quiet = start_prompt();
    answered(cliclack::input(prompt).interact())
}

/// Writes a message line with the symbol of its tone. A [`Tone::Detail`] line continues the frame without a symbol of
/// its own.
pub fn message(tone: Tone, text: &str) {
    follow_stderr_colors();
    let _ = match tone {
        Tone::Info => cliclack::log::info(text),
        Tone::Success => cliclack::log::success(text),
        Tone::Warning => cliclack::log::warning(text),
        Tone::Error => cliclack::log::error(text),
        Tone::Detail => cliclack::log::remark(text),
    };
}

/// Writes the first line of the frame of a dialog, with its title.
pub fn intro(title: &str) {
    follow_stderr_colors();
    let _ = cliclack::intro(title);
}

/// Writes the last line of the frame of a dialog that reached its end.
pub fn outro(text: &str) {
    follow_stderr_colors();
    let _ = cliclack::outro(text);
}

/// Writes the last line of the frame of a dialog that stopped before its end.
pub fn outro_cancel(text: &str) {
    follow_stderr_colors();
    let _ = cliclack::outro_cancel(text);
}

/// Starts a spinner with `text` until [`Wait::done`].
pub fn wait(text: &str) -> Wait {
    follow_stderr_colors();
    let spinner = cliclack::spinner();
    spinner.start(text);
    Wait { spinner }
}

/// A running spinner, from [`wait`].
pub struct Wait {
    spinner: cliclack::ProgressBar,
}

impl Wait {
    /// Stops the spinner and writes `text` as the line of a finished step.
    pub fn done(self, text: &str) {
        self.spinner.stop(text);
    }
}

/// The number of menu rows on screen. A longer menu scrolls.
const MAX_ROWS: usize = 12;

/// The answer of a prompt: `None` for Esc or Ctrl+C, which `cliclack` both report as `Interrupted`.
fn answered<T>(answer: io::Result<T>) -> Result<Option<T>> {
    match answer {
        Ok(value) => Ok(Some(value)),
        Err(error) if error.kind() == io::ErrorKind::Interrupted => Ok(None),
        Err(error) => Err(error).context("cannot read the answer"),
    }
}

/// Prepares the terminal for one prompt: the colors, and a quiet terminal until the returned guard drops.
fn start_prompt() -> Option<quiet::QuietTerminal> {
    follow_stderr_colors();
    quiet::QuietTerminal::start()
}

/// Makes the colors of the stdout switch of `console` follow its stderr switch. The theme styles of `cliclack` read the
/// stdout switch, but the dialog draws on stderr, and stdout can be a pipe. The stderr switch is off for `NO_COLOR`
/// and for a terminal without colors.
fn follow_stderr_colors() {
    console::set_colors_enabled(console::colors_enabled_stderr());
}

mod quiet;

#[cfg(test)]
mod tests;
