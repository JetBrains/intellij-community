// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The command line of the packer.
//!
//! A packing action passes `--flagfile=<path>` alone. A one-shot run can add `--trace-file=<path>` and `--verify-crc`.
//! The parser refuses one dash, a value in the next argument, a repeated option and `--verify-crc=<bool>`, because no
//! caller uses these forms. A typo then fails the action and does not change it silently.

use std::ffi::OsString;
use std::path::PathBuf;

use anyhow::bail;

pub(crate) const USAGE: &str = "usage: content-module-packer [--verify-crc] [--trace-file=<path>] --flagfile=<path>";

#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct Options {
    /// The recipe: one `output=` line per jar, then the lines of the sources that the jar merges.
    pub(crate) flag_file: PathBuf,
    /// Calculates the CRC of each entry again and fails on a mismatch. It is for a parity run, not for a build.
    pub(crate) verify_crc: bool,
    /// The span file of a one-shot run. It wins over a `trace-file=` line of the recipe. It is UTF-8 text, because it
    /// goes through the path rule of the recipe.
    pub(crate) trace_file: Option<String>,
}

pub(crate) fn parse(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<Options> {
    let mut options = cli::parse(arguments)?;
    if options.take("--cpuprofile")?.is_some() {
        bail!("--cpuprofile is not supported: pass --trace-file=<path>, and the spans show where the run spends its time");
    }
    let flag_file = PathBuf::from(options.require("--flagfile")?);
    let trace_file = options.take("--trace-file")?;
    if trace_file.as_deref() == Some("") {
        bail!("--trace-file must not be empty");
    }
    let verify_crc = options.flag("--verify-crc")?;
    options.finish()?;
    Ok(Options {
        flag_file,
        verify_crc,
        trace_file,
    })
}
