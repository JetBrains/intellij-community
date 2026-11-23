// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `runtime-layout` writes the layout file that the runtime module repository generator reads. The file is the
//! `RuntimeModuleRepositoryLayout` of `RuntimeModuleRepositoryMain`. It lists the files of each plugin of a
//! distribution, in the order in which `JarPackager` reports them.
//!
//! Each producer of plugin jars states what its jars merge in a part file. The assembly joins the parts, maps each
//! library container to its library in the project model, and derives the order of the entries:
//!
//! ```text
//! runtime-layout --part=<file>... [--frontend-only-part=<file>...] --bazel-targets=<file> --output=<file> \
//!   [--module-facts=<file> --selected-facts=<file>]
//! ```
//!
//! With `--module-facts`, the assembly also writes the module facts of the layout to `--selected-facts`, see `facts`.
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
mod facts;
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
    cli::main(run)
}

/// Runs the tool and returns the exit code: 1 for every error. A run that succeeds writes nothing.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    let mut args: Vec<OsString> = args.into_iter().collect();
    let result = if args.first().is_some_and(|first| first == "plan-part") {
        args.remove(0);
        run_plan_part(args)
    } else {
        run_assemble(args)
    };
    cli::exit_code(errors, result, 1)
}

fn run_assemble(args: Vec<OsString>) -> anyhow::Result<()> {
    let mut options = cli::parse(args)?;
    let part_files = values(&mut options, "--part")?;
    let frontend_only_files = values(&mut options, "--frontend-only-part")?;
    let bazel_targets = options.require("--bazel-targets")?;
    let output = options.require("--output")?;
    let module_facts = options.take("--module-facts")?;
    let selected_facts = options.take("--selected-facts")?;
    options.finish()?;
    if part_files.is_empty() {
        bail!("--part is required");
    }
    if module_facts.is_some() != selected_facts.is_some() {
        bail!("--module-facts and --selected-facts go together");
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
    if let (Some(module_facts), Some(selected_facts)) = (module_facts, selected_facts) {
        let table = facts::read_facts(Path::new(&module_facts))?;
        write_json(Path::new(&selected_facts), &facts::select_facts(&table, &result)?, true)?;
    }
    Ok(())
}

/// The independent libraries file of `plan-part`.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct IndependentLibraries {
    version: i64,
    libraries: Vec<Member>,
}

fn run_plan_part(args: Vec<OsString>) -> anyhow::Result<()> {
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
    Ok(())
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
