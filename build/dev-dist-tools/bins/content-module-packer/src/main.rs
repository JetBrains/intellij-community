// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `content-module-packer` packs the `lib/` jars of the content modules of a product, one jar per module, from module
//! and library jars that are already built.
//!
//! The recipe arrives as a flag file. Nothing here reads a project model, a product property or a plugin descriptor, so
//! the output is a function of the inputs alone. `jarpack` holds the recipe grammar and the bytes of the jar. This crate
//! holds the command line, the inventory of each jar, the spans and the parallel packing of a large recipe.
//!
//! The inventory is the port of `inventoryPackingOutput` of the Go `main.go`. It goes to the `metadata-file=` of the
//! group through `filemeta`, after the jar. It lists the jar, and in natives mode also the tree root and every entry under
//! it. A tree file gets `NativeTree::file_mode`, not the mode that a stat returns. A tree directory gets the mode that a
//! stat returns. The Kotlin build and the collector read the file, so its bytes are the inventory JSON version 1.
//!
//! The merge hashes the jar while it writes it, and its report holds the size and the content hash. So the inventory
//! does not read the jar again, and takes only the mode of the jar from a stat. It refuses a jar output that is not a
//! regular file. At a link, the merge wrote the bytes into another file. The inventory reads and hashes each file of a
//! tree.
//!
//! A Bazel action runs one process for one jar, so the command line of a failed action reproduces the failure. A parity
//! run or a profile run packs a whole tranche in one process, from one flag file with thousands of `output=` groups.

mod inventory;
mod options;
mod pack;
#[cfg(test)]
mod tests;

use std::ffi::OsString;
use std::fmt::Display;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use jarpack::{FlagFile, MergeOptions, MergeSpec};

/// The name of the producer in the merged build trace.
const SERVICE_NAME: &str = "content-module-packer";

/// The exit code of every failure. The Go packer used the same code.
const FAILURE: u8 = 3;

fn main() -> ExitCode {
    let mut stderr = io::stderr().lock();
    let code = match std::env::current_dir() {
        Ok(base_dir) => run(std::env::args_os().skip(1), &base_dir, &mut stderr),
        Err(error) => report_failure(&mut stderr, &error),
    };
    ExitCode::from(code)
}

/// Runs one recipe and returns the exit code of the process. The code is 0, or [`FAILURE`] for each type of failure.
///
/// A relative path resolves against `base_dir`. That applies to the flag file, to `--trace-file=` and to each path in
/// the recipe. The process passes its working directory, which is the exec root in a build. The run writes each report
/// and each error to `stderr`, and it writes nothing to stdout.
fn run(arguments: impl IntoIterator<Item = OsString>, base_dir: &Path, stderr: &mut dyn Write) -> u8 {
    let options = match options::parse(arguments) {
        Ok(options) => options,
        Err(error) => {
            cli::report(stderr, &error);
            let _ = writeln!(stderr, "{}", options::USAGE);
            return FAILURE;
        }
    };

    // A build names the trace destination in the recipe, so the parse comes before the tracer. Thus the parse is not in
    // the root span, and a recipe that does not parse writes no span file.
    let flag_file = match jarpack::parse_flag_file(&base_dir.join(&options.flag_file), base_dir) {
        Ok(flag_file) => flag_file,
        Err(error) => return report_failure(stderr, &error),
    };
    let trace_file = match trace_destination(options.trace_file.as_deref(), &flag_file, base_dir) {
        Ok(trace_file) => trace_file,
        Err(error) => return report_failure(stderr, &error),
    };
    if let Some(trace_file) = &trace_file
        && let Err(error) = check_trace_destination(trace_file, &flag_file.groups)
    {
        return report_failure(stderr, &error);
    }
    let merge_options = MergeOptions {
        verify_crc: options.verify_crc,
    };

    trace::run_traced(SERVICE_NAME, trace_file.as_deref(), FAILURE, stderr, |tracer, stderr| {
        let specs = &flag_file.groups;
        let root = tracer.span("pack content modules");
        root.tag("jars", specs.len());
        let result = pack::pack_all(specs, &merge_options, &root, stderr);
        if let Err(error) = &result {
            root.fail(&format!("{error:#}"));
        }
        root.end();
        match result {
            Ok(()) => 0,
            Err(error) => report_failure(stderr, &error),
        }
    })
}

/// Prints `ERROR:` and the error. The alternate form prints the context chain of an `anyhow::Error`.
fn report_failure(stderr: &mut dyn Write, error: &dyn Display) -> u8 {
    let _ = writeln!(stderr, "ERROR: {error:#}");
    FAILURE
}

/// Returns the file that the run writes its spans to, or `None` for no trace.
///
/// There are two channels, because the two callers cannot share one. A build passes one argument, the flag file, so the
/// rule puts a `trace-file=` line into the recipe. A one-shot run is a command line that a person types, so it takes
/// `--trace-file=`, as every other producer of span files does. The command line wins. Thus a flag file from
/// `bazel aquery` can run again with the trace in a safe location.
///
/// `jarpack::parse_flag_file` resolves the `trace-file=` line and refuses a second, different line. So
/// [`FlagFile::trace_file`] names the destination of the run.
///
/// The command line goes through [`jarpack::resolve_path`], the path rule of the recipe parser. So
/// [`check_trace_destination`] compares two paths in one form.
fn trace_destination(command_line: Option<&str>, flag_file: &FlagFile, base_dir: &Path) -> anyhow::Result<Option<PathBuf>> {
    match command_line {
        Some(trace_file) => jarpack::resolve_path(trace_file, base_dir).map(Some),
        None => Ok(flag_file.trace_file.clone()),
    }
}

/// Refuses a trace destination that is an output or an input of the recipe.
///
/// The recipe parser compares the `trace-file=` line with the metadata files and the native trees. `--trace-file=` does
/// not go through the parser, and the parser compares neither channel with the jars and the sources.
fn check_trace_destination(trace_file: &Path, specs: &[MergeSpec]) -> Result<(), String> {
    for spec in specs {
        let kind = if spec.output == trace_file {
            "a jar output"
        } else if spec.metadata_file.as_deref() == Some(trace_file) {
            "a metadata output"
        } else if spec
            .native
            .as_ref()
            .and_then(|native| native.tree.as_ref())
            .map(|tree| tree.dir.as_path())
            == Some(trace_file)
        {
            "a native tree output"
        } else if spec.sources.iter().any(|source| source.path() == trace_file) {
            "an input"
        } else {
            continue;
        };
        return Err(format!("trace destination is {kind}: {}", trace_file.display()));
    }
    Ok(())
}
