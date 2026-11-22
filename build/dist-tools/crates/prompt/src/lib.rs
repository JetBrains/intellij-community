// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The prompts of a dialog in a terminal: a menu, a yes or no question and a line of text. `inquire` draws them on
//! stderr, on macOS, Linux and Windows. Esc gives `None`. Ctrl+C restores the terminal and ends the process as SIGINT
//! does.

use std::fmt;

use anyhow::{Context as _, Result};
use crossterm::style::{StyledContent, Stylize as _};
use inquire::error::InquireResult;
use inquire::ui::RenderConfig;
use inquire::{Confirm, InquireError, Select, Text};

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
    /// The row of the menu.
    pub label: String,
    /// The text after the question once the user selected the option.
    pub answer: String,
}

impl fmt::Display for Item {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.label)
    }
}

/// Asks for one of `items`, with the cursor on `default` first. Typing filters the rows. Returns the index of the
/// option, or `None` for Esc.
pub fn select(title: &str, items: Vec<Item>, default: usize) -> Result<Option<usize>> {
    let page_size = items.len().clamp(1, 12);
    let answer = Select::new(title, items)
        .with_starting_cursor(default)
        .with_page_size(page_size)
        .with_formatter(&|option| option.value.answer.clone())
        .with_render_config(render_config())
        .raw_prompt_skippable();
    Ok(finished(answer)?.map(|option| option.index))
}

/// Asks a yes or no question. Returns `None` for Esc.
pub fn confirm(question: &str, default: bool) -> Result<Option<bool>> {
    let answer = Confirm::new(question)
        .with_default(default)
        .with_render_config(render_config())
        .prompt_skippable();
    finished(answer)
}

/// Asks for one line of text, such as a path. Returns `None` for Esc.
pub fn text(prompt: &str) -> Result<Option<String>> {
    let answer = Text::new(prompt).with_render_config(render_config()).prompt_skippable();
    finished(answer)
}

/// A message line with the mark of its tone, such as `✔` for a result. `NO_COLOR` turns the colors off.
pub fn message(tone: Tone, text: &str) -> String {
    if !ansi() {
        return format!("{} {text}", mark(tone));
    }
    let mark: StyledContent<&str> = match tone {
        Tone::Info => mark(tone).cyan(),
        Tone::Success => mark(tone).green(),
        Tone::Warning => mark(tone).yellow(),
        Tone::Error => mark(tone).red(),
        Tone::Detail => return format!("{} {}", mark(tone), text.dark_grey()),
    };
    format!("{mark} {text}")
}

/// The mark at the start of a message line.
#[must_use]
pub const fn mark(tone: Tone) -> &'static str {
    match tone {
        Tone::Info => "▸",
        Tone::Success => "✔",
        Tone::Warning => "!",
        Tone::Error => "✖",
        Tone::Detail => " ",
    }
}

/// Ends the process after Ctrl+C. `inquire` has restored the terminal at that point.
fn finished<T>(answer: InquireResult<T>) -> Result<T> {
    match answer {
        Err(InquireError::OperationInterrupted) => interrupt(),
        answer => answer.context("cannot read the answer"),
    }
}

fn render_config() -> RenderConfig<'static> {
    if std::env::var_os("NO_COLOR").is_some_and(|value| !value.is_empty()) || !ansi() {
        RenderConfig::empty()
    } else {
        RenderConfig::default_colored()
    }
}

/// Returns whether the terminal takes escape sequences. The call turns them on in the console.
#[cfg(windows)]
fn ansi() -> bool {
    crossterm::ansi_support::supports_ansi()
}

/// Returns whether the terminal takes escape sequences. Each terminal of macOS and Linux does.
#[cfg(not(windows))]
const fn ansi() -> bool {
    true
}

/// Ends the process with the default action of SIGINT, as Ctrl+C does without raw mode.
#[cfg(unix)]
fn interrupt() -> ! {
    // SAFETY: `SIG_DFL` is a valid disposition for SIGINT.
    unsafe { libc::signal(libc::SIGINT, libc::SIG_DFL) };
    // SAFETY: `raise` sends a signal to the calling thread and has no memory effects.
    unsafe { libc::raise(libc::SIGINT) };
    std::process::exit(130)
}

/// Ends the process with the exit code of Ctrl+C in a console.
#[cfg(not(unix))]
fn interrupt() -> ! {
    std::process::exit(130)
}

#[cfg(test)]
mod tests;
