//! Derives the recipe of one complex plugin from its plan file and packs the remainder against the Starlark input
//! catalogue. It also writes the asset rows and the plugin classpath record. The caller is
//! `dev_plugin_remainder_from_plan` in `dev_plugin_remainder.bzl`.
//!
//! The mode `gzip-resources` writes the gzip resources of one module instead, see [`pluginpack::write_gzip_resources`].
//! The caller is `gzip_resources` in `gzip_resources.bzl`.

use std::ffi::OsString;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::bail;
use planfile::contract::{Catalogue, TREE_VERSION, VERSION};

/// The options of the packer, each in the form `--name=value`. Every one is required.
const OPTIONS: [&str; 9] = [
    "--projection",
    "--input-catalogue",
    "--classpath-descriptor",
    "--plugin-directory",
    "--execution-version",
    "--output-dir",
    "--inventory",
    "--assets",
    "--classpath",
];

/// The first argument that selects the gzip resources mode.
const GZIP_RESOURCES_MODE: &str = "gzip-resources";

/// The output directory of the gzip resources mode. The archives follow as plain arguments.
const GZIP_OUTPUT_OPTION: &str = "--output-dir";

/// Names one module whose plain module jar the chain reuses. It repeats once per module and may be absent.
const INDEPENDENT_MODULE_OPTION: &str = "--independent-module";

/// Names one content module that the product mode of the chain refuses. It repeats once per module and may be absent.
/// An asset whose every module is refused is omitted from the remainder, see `planfile::omitted_assets`.
const REFUSED_MODULE_OPTION: &str = "--refused-module";

fn main() -> ExitCode {
    let code = run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr());
    ExitCode::from(code)
}

/// The parsed command line: the value of each option in [`OPTIONS`] order, the independent modules and the refused
/// modules.
struct Arguments {
    values: Vec<String>,
    independent_modules: Vec<String>,
    refused_modules: Vec<String>,
}

impl Arguments {
    fn value(&self, name: &str) -> &str {
        let index = OPTIONS.iter().position(|option| *option == name).expect("a known option");
        &self.values[index]
    }
}

/// Runs the packer and returns the exit code: 0 on success, 1 on a failure, 2 on a usage error.
fn run(arguments: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let mut arguments = arguments.into_iter().peekable();
    if arguments.peek().is_some_and(|argument| argument == GZIP_RESOURCES_MODE) {
        arguments.next();
        return run_gzip_resources(arguments, errors);
    }
    let arguments = match parse(arguments) {
        Ok(arguments) => arguments,
        Err(error) => {
            cli::report(errors, &error);
            return 2;
        }
    };
    let version = match arguments.value("--execution-version").parse::<u32>() {
        Ok(version) if (VERSION..=TREE_VERSION).contains(&version) => version,
        _ => {
            let _ = writeln!(errors, "ERROR: --execution-version must be 1 or 2");
            return 2;
        }
    };
    match project(&arguments, version) {
        Ok(plugin) => {
            let _ = writeln!(output, "Packed the remainder for {plugin} from its plan file");
            0
        }
        Err(message) => {
            let _ = writeln!(errors, "ERROR: {message}");
            1
        }
    }
}

/// Runs the gzip resources mode: `gzip-resources --output-dir=<dir> <archive>...`. The exit codes are the ones of
/// [`run`].
fn run_gzip_resources(arguments: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    let (output, archives) = match parse_gzip_resources(arguments) {
        Ok(parsed) => parsed,
        Err(error) => {
            cli::report(errors, &error);
            return 2;
        }
    };
    match pluginpack::write_gzip_resources(&archives, &output) {
        Ok(()) => 0,
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error}");
            1
        }
    }
}

/// Reads one `--output-dir=` option and at least one archive. An archive is a positional argument.
fn parse_gzip_resources(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<(PathBuf, Vec<PathBuf>)> {
    let mut options = cli::parse(arguments)?;
    let output = PathBuf::from(options.require(GZIP_OUTPUT_OPTION)?);
    let archives: Vec<PathBuf> = options.positionals().into_iter().map(PathBuf::from).collect();
    options.finish()?;
    if archives.is_empty() {
        bail!("expected at least one archive");
    }
    Ok((output, archives))
}

/// Reads the options. An option takes its value after `=`, and only `--independent-module` and `--refused-module`
/// repeat.
fn parse(arguments: impl IntoIterator<Item = OsString>) -> anyhow::Result<Arguments> {
    let mut options = cli::parse(arguments)?;
    let independent_modules = modules(&mut options, INDEPENDENT_MODULE_OPTION)?;
    let refused_modules = modules(&mut options, REFUSED_MODULE_OPTION)?;
    let values = OPTIONS.iter().map(|name| options.require(name)).collect::<anyhow::Result<_>>()?;
    options.finish()?;
    Ok(Arguments {
        values,
        independent_modules,
        refused_modules,
    })
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
fn project(arguments: &Arguments, version: u32) -> Result<String, String> {
    let file = planfile::read(Path::new(arguments.value("--projection"))).map_err(|error| format!("{error:#}"))?;
    let catalogue: Catalogue =
        planfile::json::read(Path::new(arguments.value("--input-catalogue"))).map_err(|error| format!("{error:#}"))?;
    let descriptor_path = arguments.value("--classpath-descriptor");
    let descriptor = fs::read(descriptor_path).map_err(|error| format!("{descriptor_path}: {error}"))?;
    let derivation = planfile::derive(
        &file,
        &catalogue,
        arguments.value("--plugin-directory"),
        &descriptor,
        version,
        &arguments.independent_modules,
        &arguments.refused_modules,
    )
    .map_err(|error| format!("{error:#}"))?;
    let execution = pluginpack::plan(&derivation.recipe, &derivation.catalogue).map_err(|error| error.to_string())?;
    let assets = serde_json::to_vec(&derivation.assets).map_err(|error| error.to_string())?;
    execution
        .write(
            Path::new(arguments.value("--output-dir")),
            Path::new(arguments.value("--inventory")),
        )
        .map_err(|error| error.to_string())?;
    write_output(arguments.value("--assets"), &assets)?;
    write_output(arguments.value("--classpath"), &derivation.class_path)?;
    Ok(file.plugin)
}

/// Writes one contract file with the mode 0644, which does not depend on the umask.
fn write_output(file: &str, data: &[u8]) -> Result<(), String> {
    let path = Path::new(file);
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        filemeta::create_dir_all_0755(parent).map_err(|error| error.to_string())?;
    }
    fs::write(path, data).map_err(|error| format!("{file}: {error}"))?;
    fscopy::set_distribution_file_mode(path, false, Some(0o644)).map_err(|error| error.to_string())
}

#[cfg(test)]
mod tests;
