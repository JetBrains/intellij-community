// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The command line of the dev-distribution tools: the options `--key=value` and `--flag`, and the positional
//! arguments.
//!
//! A Bazel rule writes the command line of an action tool. So an option has one form, and a tool refuses every other
//! form: a short option, a value in the next argument, a bare `--`, a repeated option that is not a list, and an
//! option that the tool does not take. A typo then fails the action and does not change it silently. A tool takes its
//! options one by one, then [`Options::finish`] refuses the rest. [`report`] prints an error. Each tool keeps its own
//! exit codes.
//!
//! The crate replaces `lexopt`, because each tool undid the model of `lexopt` with its own loop, and every tool is a
//! re-key trigger of its actions. The code is small, and it has no dependency that a tool does not link already.

use std::ffi::OsString;
use std::io::Write;

use anyhow::{Result, bail};

/// The arguments of one command line, in their order. An option keeps its name with the leading `--`.
#[derive(Debug, Default)]
pub struct Options {
    /// Each option with its value, or with `None` for the form `--flag`.
    options: Vec<(String, Option<String>)>,
    positionals: Vec<String>,
}

/// Reads the command line. It refuses a short option, a bare `--`, `--=value` and an argument that is not UTF-8.
pub fn parse(arguments: impl IntoIterator<Item = OsString>) -> Result<Options> {
    let mut parsed = Options::default();
    for argument in arguments {
        let argument = match argument.into_string() {
            Ok(argument) => argument,
            Err(argument) => bail!("the argument {:?} is not valid UTF-8", argument.to_string_lossy()),
        };
        if !argument.starts_with('-') {
            parsed.positionals.push(argument);
            continue;
        }
        let (name, value) = match argument.split_once('=') {
            Some((name, value)) => (name, Some(value.to_owned())),
            None => (argument.as_str(), None),
        };
        if !name.starts_with("--") || name.len() == 2 {
            bail!(form_error(&argument));
        }
        parsed.options.push((name.to_owned(), value));
    }
    Ok(parsed)
}

impl Options {
    /// Takes the value of an option that the command line states at most once. An empty value is `Some("")`.
    pub fn take(&mut self, name: &str) -> Result<Option<String>> {
        let mut values = self.remove(name);
        if values.len() > 1 {
            bail!("{name} must be specified at most once");
        }
        values.pop().map(|value| value.ok_or_else(|| takes_value(name))).transpose()
    }

    /// Takes every value of a list option, in the order of the command line.
    pub fn take_all(&mut self, name: &str) -> Result<Vec<String>> {
        self.remove(name)
            .into_iter()
            .map(|value| value.ok_or_else(|| takes_value(name)))
            .collect()
    }

    /// Takes the value of an option that the command line states once, with a value that is not empty.
    pub fn require(&mut self, name: &str) -> Result<String> {
        match self.take(name)? {
            Some(value) if !value.is_empty() => Ok(value),
            _ => bail!("{name} is required"),
        }
    }

    /// Takes a flag, an option in the form `--flag` that the command line states at most once.
    pub fn flag(&mut self, name: &str) -> Result<bool> {
        match self.remove(name).as_slice() {
            [] => Ok(false),
            [None] => Ok(true),
            [Some(_)] => bail!("{name} takes no value"),
            _ => bail!("{name} must be specified at most once"),
        }
    }

    /// Takes the positional arguments, in the order of the command line.
    pub fn positionals(&mut self) -> Vec<String> {
        std::mem::take(&mut self.positionals)
    }

    /// Refuses the options that the tool did not take, sorted by name, then the first positional argument that it did
    /// not take.
    pub fn finish(self) -> Result<()> {
        let mut unknown: Vec<&str> = self.options.iter().map(|(name, _)| name.as_str()).collect();
        unknown.sort_unstable();
        unknown.dedup();
        match unknown.as_slice() {
            [] => {}
            [name] => bail!("unknown option: {name}"),
            names => bail!("unknown options: {}", names.join(", ")),
        }
        match self.positionals.first() {
            Some(positional) => bail!(form_error(positional)),
            None => Ok(()),
        }
    }

    fn remove(&mut self, name: &str) -> Vec<Option<String>> {
        self.options
            .extract_if(.., |(option, _)| option == name)
            .map(|(_, value)| value)
            .collect()
    }
}

/// Prints the error with its causes, as `ERROR: <error>: <cause>`.
pub fn report(stderr: &mut dyn Write, error: &anyhow::Error) {
    let _ = writeln!(stderr, "ERROR: {error:#}");
}

fn form_error(argument: &str) -> String {
    format!("expected an option in the form --key=value, but got {argument:?}")
}

fn takes_value(name: &str) -> anyhow::Error {
    anyhow::anyhow!("{name} takes a value, as in {name}=<value>")
}

#[cfg(test)]
mod tests;
