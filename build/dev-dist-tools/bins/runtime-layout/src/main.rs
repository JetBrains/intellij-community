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
//! runtime-layout plan-part --plan=<file> --catalogue=<file> [--independent-libraries=<file>] [--refused-module=<module>...] \
//!   --descriptor-module=<module> --plugin-directory=plugins/<directory> --descriptor=<file> --output=<file>
//! ```
//!
//! The independent libraries file is `{"version": 1, "libraries": [{"library": <label>, "jars": [<file>...]}...]}`: the
//! libraries that the reused content module jars of the plugin merge. A refused module is a content module that the
//! product mode of the chain refuses. The part leaves out the jars that the packer omits for the refused modules.

mod assemble;
mod descriptor;
mod part;
mod plan;
mod targets;

use std::ffi::OsString;
use std::io::Write;
use std::path::Path;
use std::process::ExitCode;

use anyhow::{Context, bail};
use serde::Deserialize;

use crate::assemble::{AssembledPart, assemble};
use crate::part::{Member, PART_VERSION, PLUGIN_ORDER, read_part, write_json};

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
            cli::report(errors, &error);
            1
        }
    }
}

fn run_assemble(args: Vec<OsString>) -> anyhow::Result<String> {
    let mut options = cli::parse(args)?;
    let part_files = values(&mut options, "--part")?;
    let frontend_only_files = values(&mut options, "--frontend-only-part")?;
    let bazel_targets = options.require("--bazel-targets")?;
    let output = options.require("--output")?;
    options.finish()?;
    if part_files.is_empty() {
        bail!("--part is required");
    }
    let libraries = targets::read_library_index(Path::new(&bazel_targets))?;
    let mut parts = Vec::with_capacity(part_files.len() + frontend_only_files.len());
    for (files, frontend_only) in [(part_files.as_slice(), false), (frontend_only_files.as_slice(), true)] {
        for file in files {
            let part = read_part(Path::new(file))?;
            let mut assembled = AssembledPart {
                frontend_only,
                ..AssembledPart::default()
            };
            if part.order == PLUGIN_ORDER {
                assembled.content = descriptor::read_content_order(Path::new(&part.descriptor))?;
            }
            assembled.part = part;
            parts.push(assembled);
        }
    }
    let result = assemble(&parts, &libraries)?;
    write_json(Path::new(&output), &result, true)?;
    Ok(format!(
        "Wrote the runtime module repository layout of {} plugins to {output}",
        result.plugins.len()
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
    let mut options = cli::parse(args)?;
    let independent_library_files = values(&mut options, "--independent-libraries")?;
    let refused_modules = values(&mut options, "--refused-module")?;
    let plan_path = options.require("--plan")?;
    let catalogue = options.require("--catalogue")?;
    let descriptor_module = options.require("--descriptor-module")?;
    let plugin_directory = options.require("--plugin-directory")?;
    let descriptor = options.require("--descriptor")?;
    let output = options.require("--output")?;
    options.finish()?;
    let mut independent_libraries = Vec::new();
    for file in &independent_library_files {
        let libraries: IndependentLibraries = planfile::json::read(Path::new(file))?;
        if libraries.version != PART_VERSION {
            bail!("{file} has version {}, but {PART_VERSION} is expected", libraries.version);
        }
        independent_libraries.extend(libraries.libraries);
    }
    let plan = planfile::read(Path::new(&plan_path))?;
    let catalogue: planfile::contract::Catalogue = planfile::json::read(Path::new(&catalogue))?;
    let result = plan::part_from_plan(
        &plan,
        &catalogue,
        &independent_libraries,
        &refused_modules,
        &descriptor_module,
        &plugin_directory,
        &descriptor,
    )
    .with_context(|| plan_path.clone())?;
    write_json(Path::new(&output), &result, false)?;
    Ok(format!(
        "Wrote the layout part of {} with {} jars to {output}",
        result.descriptor_module,
        result.jars.len()
    ))
}

/// Takes every value of a list option. A value must not be empty, because each one names a file or a module.
fn values(options: &mut cli::Options, name: &str) -> anyhow::Result<Vec<String>> {
    let values = options.take_all(name)?;
    if values.iter().any(String::is_empty) {
        bail!("{name} must not be empty");
    }
    Ok(values)
}

#[cfg(test)]
mod tests;
