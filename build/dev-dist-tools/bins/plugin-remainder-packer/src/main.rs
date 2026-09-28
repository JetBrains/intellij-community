//! Derives the recipe of one complex plugin from its plan file and packs the remainder against the Starlark input
//! catalogue. It also writes the asset rows and the plugin classpath record. The caller is
//! `dev_plugin_remainder_from_plan` in `dev_plugin_remainder.bzl`.

use std::ffi::OsString;
use std::fs;
use std::io::Write;
use std::path::Path;
use std::process::ExitCode;

use planfile::contract::{Catalogue, SCOPED_VERSION, VERSION};

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
    let arguments = match parse(arguments) {
        Ok(arguments) => arguments,
        Err(message) => {
            let _ = writeln!(errors, "ERROR: {message}");
            return 2;
        }
    };
    let version = match arguments.value("--execution-version").parse::<u32>() {
        Ok(version) if (VERSION..=SCOPED_VERSION).contains(&version) => version,
        _ => {
            let _ = writeln!(errors, "ERROR: --execution-version must be 1, 2, or 3");
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

/// Reads the options. An option takes its value after `=`, and only `--independent-module` and `--refused-module`
/// repeat.
fn parse(arguments: impl IntoIterator<Item = OsString>) -> Result<Arguments, String> {
    let mut values: Vec<Option<String>> = vec![None; OPTIONS.len()];
    let mut independent_modules = Vec::new();
    let mut refused_modules = Vec::new();
    let mut parser = lexopt::Parser::from_args(arguments);
    while let Some(argument) = parser.next().map_err(|error| error.to_string())? {
        let name = match argument {
            lexopt::Arg::Long(name) => format!("--{name}"),
            lexopt::Arg::Short(name) => return Err(format!("unknown option \"-{name}\"")),
            lexopt::Arg::Value(value) => return Err(format!("unknown option {:?}", value.to_string_lossy())),
        };
        let value = parser
            .optional_value()
            .map(|value| {
                value
                    .into_string()
                    .map_err(|value| format!("{name} is not UTF-8: {}", value.display()))
            })
            .transpose()?;
        if name == INDEPENDENT_MODULE_OPTION || name == REFUSED_MODULE_OPTION {
            let modules = if name == INDEPENDENT_MODULE_OPTION {
                &mut independent_modules
            } else {
                &mut refused_modules
            };
            match value {
                Some(value) if !value.is_empty() => modules.push(value),
                _ => return Err(format!("expected a nonempty {name}=value option")),
            }
            continue;
        }
        let Some(index) = OPTIONS.iter().position(|option| *option == name) else {
            return Err(format!("unknown option {name:?}"));
        };
        match value {
            Some(value) if !value.is_empty() && values[index].is_none() => values[index] = Some(value),
            _ => return Err(format!("expected one nonempty {name}=value option")),
        }
    }
    let mut required = Vec::with_capacity(OPTIONS.len());
    for (name, value) in OPTIONS.iter().zip(values) {
        match value {
            Some(value) => required.push(value),
            None => return Err(format!("{name} is required")),
        }
    }
    Ok(Arguments {
        values: required,
        independent_modules,
        refused_modules,
    })
}

/// Derives the recipe from the plan file, packs it against the Starlark input catalogue, then writes the asset rows and
/// the plugin classpath record. A refusal happens before any write. Only an I/O failure after `Execution::write` can
/// leave the plugin directory behind, and Bazel discards the outputs of a failed action.
fn project(arguments: &Arguments, version: u32) -> Result<String, String> {
    let file = planfile::read(Path::new(arguments.value("--projection"))).map_err(|error| error.to_string())?;
    let catalogue: Catalogue = planfile::json::read(Path::new(arguments.value("--input-catalogue"))).map_err(|error| error.to_string())?;
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
    .map_err(|error| error.to_string())?;
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
        fscopy::create_dirs_0755(parent).map_err(|error| error.to_string())?;
    }
    fs::write(path, data).map_err(|error| format!("{file}: {error}"))?;
    fscopy::set_distribution_file_mode(path, false, Some(0o644)).map_err(|error| error.to_string())
}

#[cfg(test)]
mod tests;
