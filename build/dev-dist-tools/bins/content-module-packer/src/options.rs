// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The command line of the packer.
//!
//! A packing action passes `--flagfile=<path>` alone. A one-shot run can add `--trace-file=<path>` and `--verify-crc`.
//! The Go `flag` package also took one dash, a value in the next argument, a repeated option and `--verify-crc=<bool>`.
//! No caller uses these forms, so the parser refuses them. A typo then fails the action and does not change it silently.

use std::ffi::OsString;
use std::path::PathBuf;

use lexopt::Arg;

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

pub(crate) fn parse(arguments: impl IntoIterator<Item = OsString>) -> Result<Options, String> {
    let mut parser = lexopt::Parser::from_args(arguments);
    // Then `-f=x` gives the value `=x`, and the error names the option `-f`.
    parser.set_short_equals(false);
    let mut flag_file = None;
    let mut trace_file = None;
    let mut verify_crc = false;
    let mut positional = None;
    while let Some(arg) = parser.next().map_err(|error| error.to_string())? {
        let name = match arg {
            Arg::Long(name) => name.to_owned(),
            Arg::Short(first) => {
                // lexopt reads `-flagfile=x` as the option `-f` with the value `lagfile=x`.
                let rest = parser.optional_value().unwrap_or_default();
                let rest = rest.to_string_lossy();
                let name = rest.split('=').next().unwrap_or_default();
                return Err(format!(
                    "unsupported option -{first}{name}: the options take two dashes, as in --flagfile=<path>"
                ));
            }
            Arg::Value(value) => {
                // The Go `flag` package stops at the first positional argument, and this parser does the same.
                positional = Some(value);
                break;
            }
        };
        let value = parser.optional_value();
        match name.as_str() {
            "flagfile" => flag_file = Some(PathBuf::from(path_value(&name, value, flag_file.is_some())?)),
            "trace-file" => {
                let value = path_value(&name, value, trace_file.is_some())?;
                let value = value
                    .into_string()
                    .map_err(|value| format!("--trace-file= is not valid UTF-8: {:?}", value.to_string_lossy()))?;
                trace_file = Some(value);
            }
            "verify-crc" => {
                if let Some(value) = value {
                    return Err(format!(
                        "unexpected value {:?} for --verify-crc: the option takes no value",
                        value.to_string_lossy()
                    ));
                }
                if verify_crc {
                    return Err("--verify-crc is given twice".to_owned());
                }
                verify_crc = true;
            }
            "cpuprofile" => {
                return Err(
                    "--cpuprofile is not supported: pass --trace-file=<path>, and the spans show where the run spends \
                     its time"
                        .to_owned(),
                );
            }
            // The text of the Go `flag` package, which names the option with one dash.
            _ => return Err(format!("flag provided but not defined: -{name}")),
        }
    }
    let Some(flag_file) = flag_file else {
        return Err("expected a `--flagfile=` argument".to_owned());
    };
    if let Some(value) = positional {
        return Err(format!(
            "unexpected argument {:?}; the recipe is passed as a single --flagfile=",
            value.to_string_lossy()
        ));
    }
    Ok(Options {
        flag_file,
        verify_crc,
        trace_file,
    })
}

/// Reads the value of a path option. The value must follow `=` in the same argument and must not be empty.
fn path_value(name: &str, value: Option<OsString>, given: bool) -> Result<OsString, String> {
    let Some(value) = value else {
        return Err(format!(
            "--{name} expects its value after `=` in the same argument, as in --{name}=<path>"
        ));
    };
    if given {
        return Err(format!("--{name}= is given twice"));
    }
    if value.is_empty() {
        return Err(format!("expected a nonempty --{name}="));
    }
    Ok(value)
}
