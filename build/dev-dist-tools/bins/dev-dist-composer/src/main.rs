// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `dev-dist-composer` assembles the components of a dev distribution into one tree, or into launch metadata only.
//! Then it writes the files that start the IDE.
//!
//! The rule `intellij_dev_fragments_dist` runs it with `--composition-spec`, `--output-dir`, `--ide-config`,
//! `--fingerprint` and an optional `--trace-file`, each in the `--key=value` form. Every failure exits with 1.
//!
//! The composer owns the composition of the component contract. [`spec`] reads the composition spec and the source
//! bindings. [`compose`] checks the components and their destinations, then [`merge`] copies the files of a full
//! distribution, and [`local_layout`] writes the layout of launch metadata. [`plugin_classpath`] joins the plugin
//! records, [`fingerprint`] computes fingerprint v5, and [`ide_config`] writes the file of `DevIdeConfig`.

use std::ffi::OsString;
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use std::process::ExitCode;

use component::{Error, Result, classpath, manifest, paths};

use crate::compose::{ComposeOptions, DevBuildComponent};

mod compose;
mod fingerprint;
mod host_paths;
mod ide_config;
mod local_layout;
mod merge;
mod plugin_classpath;
mod spec;

#[cfg(test)]
mod test_support;
#[cfg(test)]
mod tests;

/// The service name and the root span name. The measurement notes read the root span by this name.
const JOB_NAME: &str = "compose dev distribution";

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut io::stderr()))
}

/// Runs the tool. It returns 0 when the composition succeeds and 1 for every failure.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    let mut options = match CommandLineOptions::parse(args) {
        Ok(options) => options,
        Err(error) => return report(errors, &error),
    };
    let trace_file = match options.optional_path("--trace-file") {
        Ok(trace_file) => trace_file,
        Err(error) => return report(errors, &error),
    };
    let tracer = trace_file.as_ref().map(|_| trace::Tracer::new(JOB_NAME));
    let dispatch = tracer.as_ref().map_or_else(tracing::Dispatch::none, trace::Tracer::dispatch);
    let result = tracing::dispatcher::with_default(&dispatch, || {
        let root = tracing::info_span!("compose dev distribution", componentCount = tracing::field::Empty);
        let result = root.in_scope(|| compose_dev_distribution(&mut options, &root));
        if let Err(error) = &result {
            trace::fail(&root, error);
        }
        result
    });
    let code = match result {
        Ok(()) => 0,
        Err(error) => report(errors, &error),
    };
    if let (Some(tracer), Some(trace_file)) = (&tracer, &trace_file)
        && let Err(error) = tracer.write_file(Path::new(trace_file))
    {
        let _ = writeln!(errors, "ERROR: writing the span file: {error}");
        return 1;
    }
    code
}

fn report(errors: &mut dyn Write, error: &Error) -> u8 {
    let _ = writeln!(errors, "ERROR: {error}");
    1
}

/// The options of the command line. It keeps every value of an option in the order of the command line.
///
/// Every option has the `--key=value` form, because the Starlark caller writes no other form. An empty value is an
/// absent option.
#[derive(Debug, Default)]
struct CommandLineOptions {
    /// Each name with its values, in the order of the first occurrence.
    values: Vec<(String, Vec<String>)>,
    used: Vec<String>,
}

impl CommandLineOptions {
    fn parse(args: impl IntoIterator<Item = OsString>) -> Result<Self> {
        let form_error = |arg: &str| Error::msg(format!("Expected an option in the '--key=value' form, but got '{arg}'"));
        let args: Vec<OsString> = args.into_iter().collect();
        // `lexopt` reads a bare `--` as the end of the options, so the composer refuses it first.
        if args.iter().any(|arg| arg == "--") {
            return Err(Error::msg("Unknown options: --"));
        }
        let mut options = Self::default();
        let mut parser = lexopt::Parser::from_args(args);
        while let Some(arg) = parser.next().map_err(Error::msg)? {
            let name = match arg {
                lexopt::Arg::Long(name) => format!("--{name}"),
                lexopt::Arg::Short(short) => return Err(form_error(&format!("-{short}"))),
                lexopt::Arg::Value(value) => return Err(form_error(&value.to_string_lossy())),
            };
            let Some(value) = parser.optional_value() else {
                return Err(form_error(&name));
            };
            let Ok(value) = value.into_string() else {
                return Err(Error::msg(format!("{name} has a value that is not valid UTF-8")));
            };
            match options.values.iter_mut().find(|(known, _)| *known == name) {
                Some((_, values)) => values.push(value),
                None => options.values.push((name, vec![value])),
            }
        }
        Ok(options)
    }

    /// The absolute path of an option, or `None` when the option is absent or empty.
    fn optional_path(&mut self, name: &str) -> Result<Option<String>> {
        self.used.push(name.to_owned());
        let Some((_, values)) = self.values.iter().find(|(known, _)| known == name) else {
            return Ok(None);
        };
        if values.len() != 1 {
            return Err(Error::msg(format!(
                "{name} must be specified at most once, but got {} values: [{}]",
                values.len(),
                values.join(", ")
            )));
        }
        if values[0].is_empty() {
            return Ok(None);
        }
        paths::absolute_path(&values[0]).map(Some)
    }

    fn required_path(&mut self, name: &str) -> Result<String> {
        self.optional_path(name)?
            .ok_or_else(|| Error::msg(format!("{name} is required (no value and no fallback available)")))
    }

    fn check_no_unknown_options(&self) -> Result<()> {
        let mut unknown: Vec<&str> = self
            .values
            .iter()
            .map(|(name, _)| name.as_str())
            .filter(|name| !self.used.iter().any(|used| used == name))
            .collect();
        if unknown.is_empty() {
            return Ok(());
        }
        unknown.sort_by(|first, second| paths::compare_utf16(first, second));
        Err(Error::msg(format!("Unknown options: {}", unknown.join(", "))))
    }
}

/// Checks the composition spec first, then the output options, the unknown options, the source bindings and each
/// component manifest. It removes the output directory only after all of these checks pass.
fn compose_dev_distribution(options: &mut CommandLineOptions, root: &tracing::Span) -> Result<()> {
    let spec_file = options.required_path("--composition-spec")?;
    let spec = spec::read_composition_spec(Path::new(&spec_file))?;
    let output_dir = options.required_path("--output-dir")?;
    let ide_config = options.required_path("--ide-config")?;
    let fingerprint_file = options.required_path("--fingerprint")?;
    options.check_no_unknown_options()?;
    root.record("componentCount", spec.components.len() as u64);

    let mut bindings = match (&spec.source_runfiles, &spec.source_bindings) {
        (Some(_), Some(_)) => return Err(Error::msg("Local launch metadata must not expand source bindings")),
        (Some(_), None) => None,
        (None, Some(bindings)) => Some(spec::read_source_bindings(bindings, &spec.components)?),
        // The Starlark caller always gives source bindings to a full distribution, so the composer needs no other
        // source.
        (None, None) => {
            return Err(Error::msg(format!(
                "The composition spec {spec_file} requests a full distribution without source bindings, \
                 and the composer does not support that"
            )));
        }
    };
    let mut components = Vec::with_capacity(spec.components.len());
    for entry in &spec.components {
        // A component resolves the paths of its entries against the working directory. That is the execution root
        // where its action staged them, so nothing becomes absolute here for it.
        let manifest_file = paths::absolute_path(&entry.manifest)?;
        let mut component = DevBuildComponent::new(manifest::read_component_manifest(Path::new(&manifest_file))?);
        if let Some(part) = &entry.plugin_classpath_part {
            component.plugin_classpath_part = Some(paths::absolute_path(part)?.into());
        }
        if let Some(bindings) = &mut bindings {
            // `read_source_bindings` refuses a repeated manifest, so each component takes its own bindings.
            component.source_bindings = bindings.remove(&entry.manifest);
        }
        components.push(component);
    }

    remove_output(&output_dir)?;
    let compose_options = ComposeOptions {
        plugin_classpath_prefix: match &spec.plugin_classpath_prefix {
            Some(prefix) => Some(paths::absolute_path(prefix)?.into()),
            None => None,
        },
        expected_fragments: spec.expected_fragments,
        additional_modules: spec.additional_modules,
        source_runfiles: match &spec.source_runfiles {
            Some(runfiles) => Some(compose::absolute_keys(runfiles)?),
            None => None,
        },
        source_directory_runfiles: Some(compose::absolute_keys(&spec.source_directory_runfiles)?),
    };
    let home = Path::new(&output_dir);
    let result = compose::compose_components(&components, home, &compose_options)?;
    for (file, content) in [
        (
            home.join("core-classpath.txt"),
            classpath::core_classpath_text(&result.core_class_path),
        ),
        (home.join("fingerprint.txt"), result.fingerprint.clone()),
        (fingerprint_file.into(), result.fingerprint.clone()),
    ] {
        fs::write(&file, content).map_err(|error| Error::io(&file, error))?;
    }
    ide_config::write_dev_ide_config(
        &ide_config,
        &output_dir,
        &result.main_class,
        &result.platform_prefix,
        &result.additional_modules,
    )
}

/// Removes the output of an earlier run. Bazel creates the declared output directory before the action runs.
fn remove_output(output_dir: &str) -> Result<()> {
    let removed = match fs::symlink_metadata(output_dir) {
        Ok(metadata) if metadata.is_dir() => fs::remove_dir_all(output_dir),
        Ok(_) => fs::remove_file(output_dir),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error),
    };
    removed.map_err(|error| Error::io(output_dir, error))
}
