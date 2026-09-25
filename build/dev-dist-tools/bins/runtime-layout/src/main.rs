// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `runtime-layout` writes the layout file that the runtime module repository generator reads. The file is the
//! `RuntimeModuleRepositoryLayout` of `RuntimeModuleRepositoryMain`. It lists the files of each plugin of a
//! distribution, in the order in which `JarPackager` reports them.
//!
//! Each producer of plugin jars states what its jars merge in a part file. The assembly joins the parts, maps each
//! library container to its library in the project model, and derives the order of the entries:
//!
//! ```text
//! runtime-layout --part=<file>... [--frontend-only-part=<file>...] --bazel-targets=<file> --output=<file>
//! ```
//!
//! A complex plugin states no jar at analysis time. Its part is derived from the resolved plan file and the input
//! catalogue of its chain:
//!
//! ```text
//! runtime-layout plan-part --plan=<file> --catalogue=<file> [--independent-libraries=<file>] \
//!   --descriptor-module=<module> --plugin-directory=plugins/<directory> --descriptor=<file> --output=<file>
//! ```
//!
//! The independent libraries file is `{"version": 1, "libraries": [{"library": <label>, "jars": [<file>...]}...]}`: the
//! libraries that the reused content module jars of the plugin merge.

mod assemble;
mod descriptor;
mod part;
mod plan;
mod targets;

use std::collections::HashMap;
use std::ffi::{OsStr, OsString};
use std::io::Write;
use std::path::Path;
use std::process::ExitCode;

use anyhow::{Context, bail};
use serde::Deserialize;

use crate::assemble::{AssembledPart, assemble};
use crate::part::{LAYOUT_ORDER, Member, PART_VERSION, read_part, write_json};

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

/// Runs the tool and returns the exit code: 1 for every error.
fn run(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let mut args: Vec<OsString> = args.into_iter().collect();
    let result = if args.first().is_some_and(|first| first == "plan-part") {
        args.remove(0);
        run_plan_part(args)
    } else {
        run_assemble(args)
    };
    match result {
        Ok(message) => {
            let _ = writeln!(output, "{message}");
            0
        }
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            1
        }
    }
}

fn run_assemble(args: Vec<OsString>) -> anyhow::Result<String> {
    let values = parse_options(args, &["--part", "--frontend-only-part"], &["--bazel-targets", "--output"])?;
    let Some(part_files) = values.get("--part") else {
        bail!("--part is required");
    };
    let libraries = targets::read_library_index(Path::new(&values["--bazel-targets"][0]))?;
    let frontend_only_files = values.get("--frontend-only-part").map(Vec::as_slice).unwrap_or_default();
    let mut parts = Vec::with_capacity(part_files.len() + frontend_only_files.len());
    for (files, frontend_only) in [(part_files.as_slice(), false), (frontend_only_files, true)] {
        for file in files {
            let part = read_part(Path::new(file))?;
            let mut assembled = AssembledPart {
                frontend_only,
                ..AssembledPart::default()
            };
            if part.order == LAYOUT_ORDER {
                let jar_order = std::fs::read_to_string(&part.jar_order)
                    .with_context(|| format!("{}: cannot read {}", file.to_string_lossy(), part.jar_order))?;
                for (index, line) in jar_order.lines().enumerate() {
                    if line.is_empty() {
                        bail!("{}: line {} of {} is empty", file.to_string_lossy(), index + 1, part.jar_order);
                    }
                    assembled.jar_order.push(line.to_owned());
                }
            } else {
                assembled.content = descriptor::read_content_order(Path::new(&part.descriptor))?;
            }
            assembled.part = part;
            parts.push(assembled);
        }
    }
    let result = assemble(&parts, &libraries)?;
    let output = &values["--output"][0];
    write_json(Path::new(output), &result, true)?;
    Ok(format!(
        "Wrote the runtime module repository layout of {} plugins to {}",
        result.plugins.len(),
        output.to_string_lossy()
    ))
}

/// The independent libraries file of `plan-part`.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct IndependentLibraries {
    version: i64,
    libraries: Vec<Member>,
}

fn run_plan_part(args: Vec<OsString>) -> anyhow::Result<String> {
    let values = parse_options(
        args,
        &["--independent-libraries"],
        &[
            "--plan",
            "--catalogue",
            "--descriptor-module",
            "--plugin-directory",
            "--descriptor",
            "--output",
        ],
    )?;
    let single = |name: &str| values[name][0].to_string_lossy().into_owned();
    let mut independent_libraries = Vec::new();
    for file in values.get("--independent-libraries").into_iter().flatten() {
        let libraries: IndependentLibraries = planfile::json::read(Path::new(file))?;
        if libraries.version != PART_VERSION {
            bail!(
                "{} has version {}, but {PART_VERSION} is expected",
                file.to_string_lossy(),
                libraries.version
            );
        }
        independent_libraries.extend(libraries.libraries);
    }
    let plan_path = &values["--plan"][0];
    let plan = planfile::read(Path::new(plan_path))?;
    let catalogue: planfile::contract::Catalogue = planfile::json::read(Path::new(&values["--catalogue"][0]))?;
    let result = plan::part_from_plan(
        &plan,
        &catalogue,
        &independent_libraries,
        &single("--descriptor-module"),
        &single("--plugin-directory"),
        &single("--descriptor"),
    )
    .with_context(|| plan_path.to_string_lossy().into_owned())?;
    let output = &values["--output"][0];
    write_json(Path::new(output), &result, false)?;
    Ok(format!(
        "Wrote the layout part of {} with {} jars to {}",
        result.descriptor_module,
        result.jars.len(),
        output.to_string_lossy()
    ))
}

/// Reads `--key=value` options. A key of `repeated` may occur any number of times. Every key of `required` occurs
/// exactly once. The values keep the order of the arguments.
fn parse_options(args: Vec<OsString>, repeated: &[&str], required: &[&str]) -> anyhow::Result<HashMap<String, Vec<OsString>>> {
    let form_error = |arg: &OsStr| anyhow::anyhow!("expected an option in the '--key=value' form, but got {:?}", arg.to_string_lossy());
    let mut values: HashMap<String, Vec<OsString>> = HashMap::new();
    let mut parser = lexopt::Parser::from_args(args);
    while let Some(arg) = parser.next()? {
        let name = match arg {
            lexopt::Arg::Long(name) => format!("--{name}"),
            lexopt::Arg::Short(short) => return Err(form_error(OsStr::new(&format!("-{short}")))),
            lexopt::Arg::Value(value) => return Err(form_error(&value)),
        };
        let value = match parser.optional_value() {
            Some(value) if !value.is_empty() => value,
            Some(_) => return Err(form_error(OsStr::new(&format!("{name}=")))),
            None => return Err(form_error(OsStr::new(&name))),
        };
        let is_required = required.contains(&name.as_str());
        if !is_required && !repeated.contains(&name.as_str()) {
            bail!("unknown option {name:?}");
        }
        if is_required && values.contains_key(&name) {
            bail!("{name} must be specified at most once");
        }
        values.entry(name).or_default().push(value);
    }
    for name in required {
        if !values.contains_key(*name) {
            bail!("{name} is required");
        }
    }
    Ok(values)
}

#[cfg(test)]
mod tests;
