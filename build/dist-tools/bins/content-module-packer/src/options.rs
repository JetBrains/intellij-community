// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The command line of the packer.
//!
//! A packing action passes `--flagfile=<path>` alone. The parser refuses every other option, one dash, a value in the
//! next argument and a repeated option, because no caller uses these forms. A typo then fails the action and does not
//! change it silently.

use std::ffi::OsString;
use std::path::PathBuf;

pub(crate) const USAGE: &str = "usage: content-module-packer --flagfile=<path>";

/// Returns the recipe: one `output=` line, then the lines of the sources that the jar merges.
pub(crate) fn parse(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<PathBuf> {
    let mut options = cli::parse(arguments)?;
    let flag_file = PathBuf::from(options.require("--flagfile")?);
    options.finish()?;
    Ok(flag_file)
}
