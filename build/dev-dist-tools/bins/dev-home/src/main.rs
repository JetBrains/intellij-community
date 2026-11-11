// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `dev-home` gives a launch target a home of real files outside the output base, so no IDE runs from a file that
//! Bazel owns, and Bazel can rebuild while an IDE runs.
//!
//! ```text
//! dev-home materialize --spec=<file> --format=json|sh|cmd [--output=<file>]
//! dev-home gc --root=<dir>
//! ```
//!
//! `materialize` reads the home spec of the target, builds the generation of its fingerprint when it is missing, and
//! prints the launcher and the vmoptions file of the generation in the form of `--format`. The `json` form also names
//! the launch of the target, so an IDE run handler reads nothing else. `--output` writes the answer
//! to a file instead, which the `cmd` form needs. `INTELLIJ_DEV_HOME_ROOT` replaces the root of the spec. After a new
//! generation, `materialize` starts `gc` as a detached child.
//!
//! `gc` deletes the generations and the blobs that no start needs. A successful run prints nothing.

mod gc;
mod generation;
mod holders;
mod lock;
mod output;
mod spec;
mod store;
#[cfg(test)]
mod test_support;

use std::ffi::OsString;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::{Context as _, Result, bail};

use crate::output::Format;

/// The variable that replaces the root of the spec.
const ROOT_VARIABLE: &str = "INTELLIJ_DEV_HOME_ROOT";

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

/// Runs the tool and returns the exit code: 1 for every error.
fn run(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    cli::exit_code(errors, dispatch(args, output), 1)
}

fn dispatch(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write) -> Result<()> {
    let mut options = cli::parse(args)?;
    let verb = options.positionals();
    match verb.as_slice() {
        [verb] if verb == "materialize" => {
            let spec = options.require("--spec")?;
            let format = Format::parse(&options.require("--format")?)?;
            let file = options.take("--output")?;
            options.finish()?;
            if format == Format::Cmd && file.is_none() {
                bail!("--format=cmd needs --output");
            }
            let root = std::env::var_os(ROOT_VARIABLE).filter(|value| !value.is_empty()).map(PathBuf::from);
            let text = materialize(Path::new(&spec), root, format)?;
            match file {
                Some(file) => fs::write(&file, text).with_context(|| format!("cannot write {file}")),
                None => output.write_all(text.as_bytes()).context("cannot write the answer"),
            }
        }
        [verb] if verb == "gc" => {
            let root = options.require("--root")?;
            options.finish()?;
            gc::collect(Path::new(&root), &gc::Policy::DEFAULT).map(|_| ())
        }
        _ => bail!("expected one verb, materialize or gc, got {verb:?}"),
    }
}

/// Materializes the home of the spec at `spec`, starts a collection when one is due, and returns the answer.
fn materialize(spec: &Path, root: Option<PathBuf>, format: Format) -> Result<String> {
    let spec = spec::read_spec(spec)?;
    let root = root.unwrap_or_else(|| spec.root.clone());
    fs::create_dir_all(&root).with_context(|| format!("cannot create {}", root.display()))?;
    let store = store::Store::new(&root)?;
    let materialized = generation::materialize(&spec, &root, &store)?;
    let text = output::render(&materialized, &spec.launch, format)?;
    if gc::is_due(&root, materialized.built) {
        gc::start_detached(&root);
    }
    Ok(text)
}

#[cfg(test)]
mod tests;
