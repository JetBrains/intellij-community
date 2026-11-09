// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `tool-identity` writes the identity file of an action tool: the text that changes when a build of the tool can
//! change, and that is equal on every host.
//!
//! ```text
//! tool-identity --flagfile=<file> --output=<file>
//! ```
//!
//! The flag file has one entry per line, in the order of the output:
//!
//! - `fact=<text>` writes the line `<text>`. The rule states a fact that analysis knows, such as a crate or a version.
//! - `file=<name>=<path>` writes the line `<name> <hash>`, with the content hash of the file in 16 hex digits. The name
//!   is a path that names no host and no configuration. The path is cut at the first `=` after the name.
//!
//! The tool refuses an empty line, a line of another form, an empty name and a repeated name.

use std::collections::HashSet;
use std::ffi::OsString;
use std::fmt::Write as _;
use std::fs;
use std::io::Write;
use std::path::Path;
use std::process::ExitCode;

use anyhow::{Context, bail};

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stderr()))
}

/// Runs the tool and returns the exit code: 1 for every error. A run that succeeds writes nothing.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    match write_identity(args) {
        Ok(()) => 0,
        Err(error) => {
            cli::report(errors, &error);
            1
        }
    }
}

fn write_identity(args: impl IntoIterator<Item = OsString>) -> anyhow::Result<()> {
    let mut options = cli::parse(args)?;
    let flag_file = options.require("--flagfile")?;
    let output = options.require("--output")?;
    options.finish()?;
    let entries = fs::read_to_string(&flag_file).with_context(|| format!("cannot read {flag_file}"))?;
    let identity = identity(&entries)?;
    fs::write(&output, identity).with_context(|| format!("cannot write {output}"))
}

/// Returns the identity text of the entries of a flag file.
fn identity(entries: &str) -> anyhow::Result<String> {
    let mut text = String::with_capacity(entries.len());
    let mut names = HashSet::new();
    for line in entries.lines() {
        if let Some(fact) = line.strip_prefix("fact=") {
            text.push_str(fact);
        } else if let Some(file) = line.strip_prefix("file=") {
            let Some((name, path)) = file.split_once('=') else {
                bail!("expected `file=<name>=<path>`, got {line:?}");
            };
            if name.is_empty() || path.is_empty() {
                bail!("`file=` states an empty name or path in {line:?}");
            }
            if !names.insert(name) {
                bail!("the file name {name:?} occurs twice");
            }
            let hash = xxh3::hash_file(Path::new(path)).with_context(|| format!("cannot read {path}"))?;
            let _ = write!(text, "{name} {hash:016x}");
        } else {
            bail!("expected `fact=<text>` or `file=<name>=<path>`, got {line:?}");
        }
        text.push('\n');
    }
    Ok(text)
}

#[cfg(test)]
mod tests;
