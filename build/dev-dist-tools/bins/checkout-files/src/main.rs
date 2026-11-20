// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `checkout-files` writes the files of a git checkout under the roots to stdout, each followed by a NUL byte:
//!
//! ```text
//! checkout-files <checkout> [<root>...]
//! ```
//!
//! The answer is that of `git ls-files -z --cached --others --exclude-standard -- <roots>` without the `git` executable,
//! with the paths that do not exist and the directories left out. No root, or the root `""`, is the whole checkout.
//! `checkout_files::list` states the rules.

use std::ffi::OsString;
use std::io::{self, BufWriter, Write};
use std::path::Path;
use std::process::ExitCode;

use anyhow::{Context, bail};

// 263 only: the `cli` crate of this branch has no `main` and no `exit_code`, so the binary parses the arguments and reports an
// error itself. See `plugins/air/BRANCH-263-DIVERGENCE.md` section 4.
fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut io::stderr()))
}

/// Runs the tool and returns the exit code: 1 for every error, after the message on `errors`.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    match write_list(args, &mut io::stdout().lock()) {
        Ok(()) => 0,
        Err(error) => {
            cli::report(errors, &error);
            1
        }
    }
}

fn write_list(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write) -> anyhow::Result<()> {
    let mut options = cli::parse(args)?;
    let positionals = options.positionals();
    options.finish()?;
    let Some((checkout, roots)) = positionals.split_first() else {
        bail!("expected the arguments <checkout> [<root>...]");
    };
    let roots: Vec<&str> = roots.iter().map(String::as_str).collect();
    let files = checkout_files::list(Path::new(checkout), &roots)?;
    let mut output = BufWriter::new(output);
    for file in &files {
        output.write_all(file.as_bytes()).context("cannot write stdout")?;
        output.write_all(b"\0").context("cannot write stdout")?;
    }
    output.flush().context("cannot write stdout")
}
