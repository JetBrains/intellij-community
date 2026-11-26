// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `content-module-packer` packs the `lib/` jars of the content modules of a product, one jar per module, from module
//! and library jars that are already built.
//!
//! The recipe arrives as a flag file. Nothing here reads a project model, a product property or a plugin descriptor, so
//! the output is a function of the inputs alone. `jarpack` holds the recipe grammar and the bytes of the jar. This crate
//! holds the command line, the inventory of the jar and the spans.
//!
//! The inventory goes to the `metadata-file=` of the recipe through `filemeta`, after the jar. It lists the jar, and in
//! natives mode also the tree root and every entry under it. A tree file gets `NativeTree::file_mode`, not the mode that
//! a stat returns. A tree directory gets the mode that a stat returns. The Kotlin build and the collector read the file,
//! so its bytes are the inventory JSON version 2.
//!
//! The merge hashes the jar while it writes it, and its report holds the size and the content hash. So the inventory
//! does not read the jar again, and takes only the mode of the jar from a stat. It refuses a jar output that is not a
//! regular file. At a link, the merge wrote the bytes into another file. The inventory reads and hashes each file of a
//! tree.
//!
//! A Bazel action runs one process for one jar, so the command line of a failed action reproduces the failure.

mod inventory;
mod options;
mod pack;
#[cfg(test)]
mod tests;

use std::ffi::OsString;
use std::fmt::Display;
use std::io::{self, Write};
use std::path::Path;
use std::process::ExitCode;

/// The name of the producer in the merged build trace.
const SERVICE_NAME: &str = "content-module-packer";

/// The exit code of every failure.
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
/// A relative path resolves against `base_dir`. That applies to the flag file and to each path in the recipe. The process passes its working directory, which is the exec root in a build. A run that succeeds writes
/// nothing. A failed run writes its error to `stderr`, and it writes nothing to stdout.
fn run(arguments: impl IntoIterator<Item = OsString>, base_dir: &Path, stderr: &mut dyn Write) -> u8 {
    let flag_file = match options::parse(arguments) {
        Ok(flag_file) => flag_file,
        Err(error) => {
            cli::report(stderr, &error);
            let _ = writeln!(stderr, "{}", options::USAGE);
            return FAILURE;
        }
    };

    // The recipe names the trace destination, so the parse comes before the tracer. Thus the parse is not in the root
    // span, and a recipe that does not parse writes no span file.
    let recipe = match jarpack::parse_flag_file(&base_dir.join(&flag_file), base_dir) {
        Ok(recipe) => recipe,
        Err(error) => return report_failure(stderr, &error),
    };

    trace::run_traced(SERVICE_NAME, recipe.trace_file.as_deref(), FAILURE, stderr, |tracer, stderr| {
        let root = tracer.span("pack content modules");
        let result = pack::pack_one(&recipe.spec, &root, stderr);
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
