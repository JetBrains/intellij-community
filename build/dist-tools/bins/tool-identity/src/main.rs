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
//! - `locked=<crate>` writes nothing. It names a crate of the tool by its rustc name.
//! - `lock=<name>=<path>` reads the `Cargo.lock` at the path. It writes the line `<name> <package> <version> <source>
//!   <checksum>` for each package whose name, with `-` as `_`, is a `locked=` crate of the flag file, in the order of
//!   the lock file. An absent source or checksum is `-`. So a package that the tool does not link leaves the identity
//!   unchanged.
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
    cli::main(run)
}

/// Runs the tool and returns the exit code: 1 for every error. A run that succeeds writes nothing.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    cli::exit_code(errors, write_identity(args), 1)
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
    let mut locked = HashSet::new();
    for line in entries.lines() {
        if let Some(name) = line.strip_prefix("locked=") {
            if name.is_empty() {
                bail!("`locked=` states an empty crate name");
            }
            locked.insert(name);
        }
    }
    let mut text = String::with_capacity(entries.len());
    let mut names = HashSet::new();
    for line in entries.lines() {
        if line.starts_with("locked=") {
            continue;
        }
        if let Some(fact) = line.strip_prefix("fact=") {
            text.push_str(fact);
        } else if let Some(file) = line.strip_prefix("file=") {
            let (name, path) = named_path(line, file, "file", &mut names)?;
            let hash = xxh3::hash_file(Path::new(path)).with_context(|| format!("cannot read {path}"))?;
            let _ = write!(text, "{name} {hash:016x}");
        } else if let Some(lock) = line.strip_prefix("lock=") {
            let (name, path) = named_path(line, lock, "lock", &mut names)?;
            let data = fs::read_to_string(path).with_context(|| format!("cannot read {path}"))?;
            let packages = locked_packages(&data, &locked).with_context(|| path.to_owned())?;
            let lines: Vec<String> = packages.iter().map(|package| format!("{name} {package}")).collect();
            text.push_str(&lines.join("\n"));
            if lines.is_empty() {
                continue;
            }
        } else {
            bail!("expected `fact=<text>`, `file=<name>=<path>`, `locked=<crate>` or `lock=<name>=<path>`, got {line:?}");
        }
        text.push('\n');
    }
    Ok(text)
}

/// Splits `<name>=<path>` of an entry of `kind` and records the name. A name occurs once in a flag file.
fn named_path<'a>(line: &str, value: &'a str, kind: &str, names: &mut HashSet<&'a str>) -> anyhow::Result<(&'a str, &'a str)> {
    let Some((name, path)) = value.split_once('=') else {
        bail!("expected `{kind}=<name>=<path>`, got {line:?}");
    };
    if name.is_empty() || path.is_empty() {
        bail!("`{kind}=` states an empty name or path in {line:?}");
    }
    if !names.insert(name) {
        bail!("the file name {name:?} occurs twice");
    }
    Ok((name, path))
}

/// Returns `<package> <version> <source> <checksum>` of each `[[package]]` of a `Cargo.lock` whose name, with `-` as
/// `_`, is in `locked`, in the order of the file. The parse reads the four keys of a package and skips every other line.
fn locked_packages(data: &str, locked: &HashSet<&str>) -> anyhow::Result<Vec<String>> {
    #[derive(Default)]
    struct Package<'a> {
        name: Option<&'a str>,
        version: Option<&'a str>,
        source: Option<&'a str>,
        checksum: Option<&'a str>,
    }
    fn finish(package: &Package<'_>, locked: &HashSet<&str>, result: &mut Vec<String>) -> anyhow::Result<()> {
        let (Some(name), Some(version)) = (package.name, package.version) else {
            bail!("a [[package]] states no name or no version");
        };
        if locked.contains(name.replace('-', "_").as_str()) {
            result.push(format!(
                "{name} {version} {} {}",
                package.source.unwrap_or("-"),
                package.checksum.unwrap_or("-")
            ));
        }
        Ok(())
    }
    let mut result = Vec::new();
    let mut current: Option<Package<'_>> = None;
    for line in data.lines() {
        if line == "[[package]]" {
            if let Some(package) = current.take() {
                finish(&package, locked, &mut result)?;
            }
            current = Some(Package::default());
            continue;
        }
        let Some(package) = current.as_mut() else {
            continue;
        };
        let Some((key, value)) = line.split_once(" = ") else {
            continue;
        };
        let value = || value.strip_prefix('"').and_then(|value| value.strip_suffix('"'));
        match key {
            "name" => package.name = value(),
            "version" => package.version = value(),
            "source" => package.source = value(),
            "checksum" => package.checksum = value(),
            _ => {}
        }
    }
    if let Some(package) = current {
        finish(&package, locked, &mut result)?;
    }
    Ok(result)
}

#[cfg(test)]
mod tests;
