//! Derives the recipe of one complex plugin from its plan file and packs the remainder against the Starlark input
//! catalogue. It also writes the asset rows and the plugin classpath record. The caller is
//! `dev_plugin_remainder_from_plan` in `dev_plugin_remainder.bzl`.
//!
//! `--part` splits one remainder into two actions. The part `neutral` packs every asset that reads no descriptor, in the
//! default configuration, and writes no asset rows and no classpath record. The part `descriptor` packs only the assets
//! that read the descriptor, in the stamp class of the chain, and writes the rows and the record of the whole plugin.
//! Both parts state the descriptor assets with `--descriptor-asset`, and the packer refuses a list that differs from the
//! plan file.
//!
//! The mode `gzip-resources` writes the gzip resources of one module instead, see [`pluginpack::write_gzip_resources`].
//! The caller is `gzip_resources` in `gzip_resources.bzl`.
//!
//! Both modes exit with 0 on success, 1 on a failure, and 2 on a usage error. A failure prints one line, `ERROR:` and the
//! error with its causes.

use std::ffi::OsString;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use std::collections::HashSet;

use anyhow::{Context as _, bail};
use planfile::contract::{Catalogue, LayoutAssets, Operation, Producer, Recipe, Source, TREE_VERSION, VERSION};

/// The first argument that selects the gzip resources mode.
const GZIP_RESOURCES_MODE: &str = "gzip-resources";

fn main() -> ExitCode {
    let code = run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr());
    ExitCode::from(code)
}

/// The part of a split remainder that one action packs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Part {
    /// Every asset that reads no descriptor, in the default configuration.
    Neutral,
    /// Only the assets that read the descriptor, with the asset rows and the classpath record of the whole plugin.
    Descriptor,
}

/// The command line of the packer. Each option has the form `--name=value`, and only the module lists and the
/// descriptor assets repeat.
struct Options {
    /// `--projection`: the plan file.
    projection: PathBuf,
    /// `--input-catalogue`: the Starlark input catalogue.
    input_catalogue: PathBuf,
    /// `--classpath-descriptor`: the plugin descriptor of the classpath record. The neutral part names none.
    classpath_descriptor: Option<PathBuf>,
    /// `--plugin-directory`: the directory of the plugin in the distribution.
    plugin_directory: String,
    /// `--execution-version`: the contract version of the recipe, 1 or 2.
    execution_version: u32,
    /// `--output-dir`: the plugin directory that the packer writes.
    output_dir: PathBuf,
    /// `--inventory`: the inventory of the plugin directory.
    inventory: PathBuf,
    /// `--assets`: the asset rows. The neutral part writes none.
    assets: Option<PathBuf>,
    /// `--classpath`: the plugin classpath record. The neutral part writes none.
    classpath: Option<PathBuf>,
    /// `--part`: the part of a split remainder, or `None` for one action that packs every asset.
    part: Option<Part>,
    /// `--descriptor-asset`: each asset that reads the descriptor. A split remainder states them, and no other one.
    descriptor_assets: Vec<String>,
    /// `--independent-module`: each module whose plain module jar the chain reuses. It can be absent.
    independent_modules: Vec<String>,
    /// `--refused-module`: each content module that the product mode of the chain refuses. It can be absent. An asset
    /// whose every module is refused is omitted from the remainder, see `planfile::omitted_assets`.
    refused_modules: Vec<String>,
}

impl Options {
    /// Reads the options. Every option except the module lists is required. The version check comes after the check of
    /// the option forms.
    fn parse(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<Self> {
        let mut options = cli::parse(arguments)?;
        let independent_modules = modules(&mut options, "--independent-module")?;
        let refused_modules = modules(&mut options, "--refused-module")?;
        let descriptor_assets = modules(&mut options, "--descriptor-asset")?;
        let part = match options.take("--part")?.as_deref() {
            None => None,
            Some("neutral") => Some(Part::Neutral),
            Some("descriptor") => Some(Part::Descriptor),
            Some(other) => bail!("--part must be neutral or descriptor, not {other:?}"),
        };
        let projection = PathBuf::from(options.require("--projection")?);
        let input_catalogue = PathBuf::from(options.require("--input-catalogue")?);
        let plugin_directory = options.require("--plugin-directory")?;
        let execution_version = options.require("--execution-version")?;
        let output_dir = PathBuf::from(options.require("--output-dir")?);
        let inventory = PathBuf::from(options.require("--inventory")?);
        // The neutral part reads no descriptor and writes no rows and no record, so it takes none of these options.
        let (classpath_descriptor, assets, classpath) = if part == Some(Part::Neutral) {
            if !refused_modules.is_empty() {
                bail!("the neutral part packs every asset, so it takes no --refused-module");
            }
            (None, None, None)
        } else {
            (
                Some(PathBuf::from(options.require("--classpath-descriptor")?)),
                Some(PathBuf::from(options.require("--assets")?)),
                Some(PathBuf::from(options.require("--classpath")?)),
            )
        };
        options.finish()?;
        if part.is_some() == descriptor_assets.is_empty() {
            bail!("--part and --descriptor-asset are stated together");
        }
        let execution_version = match execution_version.parse::<u32>() {
            Ok(version) if (VERSION..=TREE_VERSION).contains(&version) => version,
            _ => bail!("--execution-version must be 1 or 2"),
        };
        Ok(Self {
            projection,
            input_catalogue,
            classpath_descriptor,
            plugin_directory,
            execution_version,
            output_dir,
            inventory,
            assets,
            classpath,
            part,
            descriptor_assets,
            independent_modules,
            refused_modules,
        })
    }
}

/// Runs the packer and returns the exit code: 0 on success, 1 on a failure, 2 on a usage error. It prints an error once,
/// through `cli::report`.
fn run(arguments: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let mut arguments = arguments.into_iter().peekable();
    if arguments.peek().is_some_and(|argument| argument == GZIP_RESOURCES_MODE) {
        arguments.next();
        return run_gzip_resources(arguments, errors);
    }
    let Some(options) = cli::ok_or_report(errors, Options::parse(arguments)) else {
        return 2;
    };
    match project(&options) {
        Ok(plugin) => {
            let _ = writeln!(output, "Packed the remainder for {plugin} from its plan file");
            0
        }
        Err(error) => {
            cli::report(errors, &error);
            1
        }
    }
}

/// Runs the gzip resources mode: `gzip-resources --output-dir=<dir> <archive>...`. The exit codes are the ones of
/// [`run`].
fn run_gzip_resources(arguments: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    let Some((output, archives)) = cli::ok_or_report(errors, parse_gzip_resources(arguments)) else {
        return 2;
    };
    match pluginpack::write_gzip_resources(&archives, &output) {
        Ok(()) => 0,
        Err(error) => {
            cli::report(errors, &error);
            1
        }
    }
}

/// Reads one `--output-dir=` option and at least one archive. An archive is a positional argument.
fn parse_gzip_resources(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<(PathBuf, Vec<PathBuf>)> {
    let mut options = cli::parse(arguments)?;
    let output = PathBuf::from(options.require("--output-dir")?);
    let archives: Vec<PathBuf> = options.positionals().into_iter().map(PathBuf::from).collect();
    options.finish()?;
    if archives.is_empty() {
        bail!("expected at least one archive");
    }
    Ok((output, archives))
}

/// Takes the modules of a list option. A module name must not be empty.
fn modules(options: &mut cli::Options, name: &str) -> anyhow::Result<Vec<String>> {
    let modules = options.take_all(name)?;
    if modules.iter().any(String::is_empty) {
        bail!("{name} must not be empty");
    }
    Ok(modules)
}

/// Derives the recipe from the plan file, packs it against the Starlark input catalogue, then writes the asset rows and
/// the plugin classpath record. A refusal happens before any write. Only an I/O failure after `Execution::write` can
/// leave the plugin directory behind, and Bazel discards the outputs of a failed action.
fn project(options: &Options) -> anyhow::Result<String> {
    let file = planfile::read(&options.projection)?;
    let catalogue: Catalogue = planfile::json::read(&options.input_catalogue)?;
    if options.part.is_some() {
        let mut stated: Vec<&str> = options.descriptor_assets.iter().map(String::as_str).collect();
        let mut planned = planfile::descriptor_assets(&file);
        stated.sort_unstable();
        planned.sort_unstable();
        if stated != planned {
            bail!(
                "plugin {:?}: the rule states the descriptor assets {stated:?}, and the plan file has {planned:?}",
                file.plugin
            );
        }
    }
    if options.part == Some(Part::Neutral) {
        let (neutral, catalogue) = planfile::without_assets(&file, &catalogue, &options.descriptor_assets)?;
        let derivation = planfile::derive(
            &neutral,
            &catalogue,
            &options.plugin_directory,
            &[],
            options.execution_version,
            &options.independent_modules,
            &[],
        )?;
        let execution = pluginpack::plan(&derivation.recipe, &derivation.catalogue)?;
        execution.write(&options.output_dir, &options.inventory)?;
        return Ok(file.plugin);
    }
    let classpath_descriptor = options
        .classpath_descriptor
        .as_ref()
        .expect("the parser requires it outside the neutral part");
    let descriptor = fs::read(classpath_descriptor).with_context(|| classpath_descriptor.display().to_string())?;
    let derivation = planfile::derive(
        &file,
        &catalogue,
        &options.plugin_directory,
        &descriptor,
        options.execution_version,
        &options.independent_modules,
        &options.refused_modules,
    )?;
    let (recipe, catalogue) = if options.part == Some(Part::Descriptor) {
        only_assets(&derivation.recipe, &derivation.catalogue, &options.descriptor_assets)
    } else {
        (derivation.recipe.clone(), derivation.catalogue.clone())
    };
    let execution = pluginpack::plan(&recipe, &catalogue)?;
    let assets = serde_json::to_vec(&derivation.assets)?;
    execution.write(&options.output_dir, &options.inventory)?;
    write_output(
        options.assets.as_ref().expect("the parser requires it outside the neutral part"),
        &assets,
    )?;
    write_output(
        options.classpath.as_ref().expect("the parser requires it outside the neutral part"),
        &derivation.class_path,
    )?;
    Ok(file.plugin)
}

/// The recipe and the catalogue of the remainder assets of `destinations` alone. The catalogue keeps only the artifacts
/// that their operations read, because the plan step refuses an unused input.
fn only_assets(recipe: &Recipe, catalogue: &Catalogue, destinations: &[String]) -> (Recipe, Catalogue) {
    let selected: HashSet<&str> = destinations.iter().map(String::as_str).collect();
    let operations: Vec<Operation> = recipe
        .operations
        .iter()
        .filter(|operation| selected.contains(operation.destination()))
        .cloned()
        .collect();
    let mut read = HashSet::new();
    for operation in &operations {
        operation_artifacts(operation, &mut read);
    }
    let recipe = Recipe {
        version: recipe.version,
        plugin: recipe.plugin.clone(),
        assets: recipe
            .assets
            .iter()
            .filter(|asset| asset.producer == Producer::Remainder && selected.contains(asset.destination.as_str()))
            .cloned()
            .collect(),
        operations,
    };
    let catalogue = Catalogue {
        version: catalogue.version,
        artifacts: catalogue
            .artifacts
            .iter()
            .filter(|artifact| read.contains(&artifact.id))
            .cloned()
            .collect(),
        libraries: Vec::new(),
    };
    (recipe, catalogue)
}

/// Adds each catalogue artifact that `operation` reads.
fn operation_artifacts(operation: &Operation, read: &mut HashSet<String>) {
    let layout = |layout: &LayoutAssets, read: &mut HashSet<String>| {
        read.extend(layout.inputs.iter().map(|reference| reference.artifact.clone()));
    };
    match operation {
        Operation::Jar { sources, .. } => {
            for source in sources {
                match source {
                    Source::Archive { input, .. } | Source::Patch { input, .. } => {
                        read.insert(input.artifact.clone());
                    }
                    Source::Layout(assets) => layout(assets, read),
                }
            }
        }
        Operation::Copy { input, .. } | Operation::CopyTree { input, .. } => {
            read.insert(input.artifact.clone());
        }
        Operation::LayoutTree { layout: assets, .. } => layout(assets, read),
    }
}

/// Writes one contract file with the mode 0644, which does not depend on the umask.
fn write_output(path: &Path, data: &[u8]) -> anyhow::Result<()> {
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        filemeta::create_dir_all_0755(parent)?;
    }
    fs::write(path, data).with_context(|| path.display().to_string())?;
    fscopy::set_distribution_file_mode(path, false, Some(0o644))?;
    Ok(())
}

#[cfg(test)]
mod tests;
