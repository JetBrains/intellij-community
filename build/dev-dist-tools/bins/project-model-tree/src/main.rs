// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `project-model-tree` lays the declared JPS project model files out as a checkout-shaped tree, for the
//! dev-distribution actions that load the project model.
//!
//! A Bazel action has no checkout. It names every model file that it depends on in a manifest, and this tool copies
//! them to the paths that the model loader expects. Rows are tab-separated:
//!
//! ```text
//! copy<TAB>path/to/the/input<TAB>relative/destination/in/the/tree
//! create<TAB><TAB>relative/destination/in/the/tree
//! ```
//!
//! `create` rows carry the repository marker files. They are not optional: `IdeaProjectLoaderUtil` searches upwards for
//! `.ultimate.root.marker`, so a tree without it is not a repository.
//!
//! Twin: `JpsModuleToBazelTargetsOnly` reads the same manifest format in the standalone `jps_to_bazel` project. The two
//! cannot share code, so a change to the format has to be made in both.

use std::collections::HashSet;
use std::ffi::OsString;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::{Context, bail};
use rayon::prelude::*;

/// The number of rows that one task copies. A model is thousands of small files, so one file per task would spend more
/// on scheduling than on copying.
const CHUNK_SIZE: usize = 512;

const JOB_NAME: &str = "materialize project model tree";

struct Options {
    manifest: PathBuf,
    output_dir: PathBuf,
    trace_file: Option<PathBuf>,
}

#[derive(Debug)]
enum Action {
    /// Copies the source, an execution-root path.
    Copy(PathBuf),
    /// Creates an empty file.
    Create,
}

#[derive(Debug)]
struct Row {
    action: Action,
    destination: PathBuf,
}

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

/// Runs the tool and returns the exit code: 2 for a usage error, 1 for a failed copy or a failed span file.
fn run(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let options = match parse_options(args) {
        Ok(options) => options,
        Err(error) => {
            cli::report(errors, &error);
            return 2;
        }
    };
    trace::run_traced(JOB_NAME, options.trace_file.as_deref(), 1, errors, |tracer, errors| {
        let root = tracer.span(JOB_NAME);
        let result = materialize(&options.manifest, &options.output_dir);
        match &result {
            Ok(count) => root.tag("files", *count),
            Err(error) => root.fail(&format_args!("{error:#}")),
        }
        root.end();
        match result {
            Ok(_) => {
                let _ = writeln!(output, "Project model tree materialized into {}", options.output_dir.display());
                0
            }
            Err(error) => {
                cli::report(errors, &error);
                1
            }
        }
    })
}

fn parse_options(args: impl IntoIterator<Item = OsString>) -> anyhow::Result<Options> {
    let mut options = cli::parse(args)?;
    let manifest = fscopy::absolute_path(Path::new(&options.require("--project-manifest")?))?;
    let output_dir = fscopy::absolute_path(Path::new(&options.require("--output-dir")?))?;
    let trace_file = match options.take("--trace-file")? {
        Some(value) if value.is_empty() => bail!("--trace-file must not be empty"),
        Some(value) => Some(fscopy::absolute_path(Path::new(&value))?),
        None => None,
    };
    options.finish()?;
    Ok(Options {
        manifest,
        output_dir,
        trace_file,
    })
}

/// Rebuilds `target` from the manifest and returns the number of rows that it wrote.
fn materialize(manifest: &Path, target: &Path) -> anyhow::Result<usize> {
    let rows = read_rows(manifest, target)?;

    // Rebuilt from scratch every time: a file left over from a previous run is a model entry that nothing declares.
    match std::fs::remove_dir_all(target) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).with_context(|| format!("cannot remove {}", target.display())),
    }
    std::fs::create_dir_all(target).with_context(|| format!("cannot create the directory {}", target.display()))?;
    // Up front and on one thread, so that the workers never race on the ancestors that they share.
    for row in &rows {
        if let Some(parent) = row.destination.parent() {
            std::fs::create_dir_all(parent).with_context(|| format!("cannot create the directory {}", parent.display()))?;
        }
    }

    // Each chunk stops at its first error, and the first failed chunk in manifest order names the error.
    let results: Vec<anyhow::Result<()>> = rows.par_chunks(CHUNK_SIZE).map(|chunk| chunk.iter().try_for_each(write)).collect();
    results.into_iter().collect::<anyhow::Result<()>>()?;
    Ok(rows.len())
}

/// Reads the rows that `project_model_manifest.bzl` writes. It refuses every other shape before the tree is removed.
/// It also refuses a repeated destination. The copy of `fscopy` checks for an existing file before it copies, so two
/// parallel rows can both pass the check.
fn read_rows(manifest: &Path, target: &Path) -> anyhow::Result<Vec<Row>> {
    let content = std::fs::read_to_string(manifest).with_context(|| format!("cannot read {}", manifest.display()))?;
    let mut rows = Vec::new();
    let mut destinations = HashSet::new();
    for line in content.lines() {
        let mut fields = line.split('\t');
        let (action, destination) = match (fields.next(), fields.next(), fields.next(), fields.next()) {
            (Some("copy"), Some(source), Some(destination), None) if !source.is_empty() => {
                (Action::Copy(PathBuf::from(source)), destination)
            }
            (Some("create"), Some(""), Some(destination), None) => (Action::Create, destination),
            _ => bail!(
                "the project model manifest row {line:?} is neither 'copy<TAB>source<TAB>destination' nor \
                 'create<TAB><TAB>destination'"
            ),
        };
        if !destinations.insert(destination) {
            bail!("the project model manifest names the destination {destination:?} more than once");
        }
        rows.push(Row {
            action,
            destination: destination_in(target, destination)?,
        });
    }
    Ok(rows)
}

/// Joins `relative` to `target`. The manifest writer maps an execution-root path to a checkout path, so a destination
/// is a relative path of plain names. The function refuses `.`, `..`, an empty name and `\`.
fn destination_in(target: &Path, relative: &str) -> anyhow::Result<PathBuf> {
    if relative.contains('\\') || relative.split('/').any(|name| matches!(name, "" | "." | "..")) {
        bail!("the project model manifest destination {relative:?} is not a relative path of plain names");
    }
    Ok(target.join(relative))
}

fn write(row: &Row) -> anyhow::Result<()> {
    match &row.action {
        // A missing source fails here rather than quietly producing a thinner project model.
        Action::Copy(source) => fscopy::clone_or_copy(source, &row.destination)?,
        Action::Create => {
            std::fs::File::create_new(&row.destination).with_context(|| format!("cannot create {}", row.destination.display()))?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests;
