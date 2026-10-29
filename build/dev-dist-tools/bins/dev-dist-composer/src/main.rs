// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `dev-dist-composer` assembles the components of a dev distribution into one tree, or into launch metadata only.
//! Then it writes the files that start the IDE.
//!
//! The rule `intellij_dev_fragments_dist` runs it with `--composition-spec`, `--output-dir`, `--ide-config`,
//! `--fingerprint` and an optional `--trace-file`, each in the `--key=value` form. Launch metadata can also name
//! `--core-classpath-file` and `--plugin-classpath-file`, which get a copy of the two metadata files of the output.
//! An optional `--home-prefix` names one directory below the output, such as `Contents` for a macOS app. Then the
//! composer writes every file below that directory, and `DevIdeConfig` names it as the home. Every failure exits with 1.
//!
//! The composer owns the composition of the component contract. [`spec`] reads the composition spec and the source
//! bindings. [`compose`] checks the components and their destinations, then [`merge`] copies the files of a full
//! distribution. [`compose`] then checks the launch class path of the copied `product-info.json`. [`local_layout`]
//! writes the layout of launch metadata. [`plugin_classpath`] joins the plugin
//! records, [`fingerprint`] computes fingerprint v5, and [`ide_config`] writes the file of `DevIdeConfig`.
//! [`placement`] checks the home placement of the spec against the manifests.

use std::ffi::OsString;
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::{Context as _, Result, bail};
use component::{classpath, manifest, paths};

use crate::compose::{ComposeOptions, DevBuildComponent};

mod compose;
mod fingerprint;
mod ide_config;
mod local_layout;
mod merge;
mod placement;
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
    let mut options = match cli::parse(args) {
        Ok(options) => options,
        Err(error) => return report(errors, &error),
    };
    let trace_file = match optional_path(&mut options, "--trace-file") {
        Ok(trace_file) => trace_file,
        Err(error) => return report(errors, &error),
    };
    trace::run_traced(JOB_NAME, trace_file.as_deref(), 1, errors, |tracer, errors| {
        let root = tracer.span(JOB_NAME);
        let result = compose_dev_distribution(options, &root);
        if let Err(error) = &result {
            root.fail(&format_args!("{error:#}"));
        }
        root.end();
        match result {
            Ok(()) => 0,
            Err(error) => report(errors, &error),
        }
    })
}

fn report(errors: &mut dyn Write, error: &anyhow::Error) -> u8 {
    cli::report(errors, error);
    1
}

/// The absolute path of an option, or `None` when the option is absent or empty.
///
/// Every option has the `--key=value` form, because the Starlark caller writes no other form. An empty value is an
/// absent option.
fn optional_path(options: &mut cli::Options, name: &str) -> Result<Option<PathBuf>> {
    match options.take(name)? {
        Some(value) if !value.is_empty() => paths::absolute_path(&value).map(Some),
        _ => Ok(None),
    }
}

fn required_path(options: &mut cli::Options, name: &str) -> Result<PathBuf> {
    paths::absolute_path(options.require(name)?)
}

/// Checks the composition spec first, then the output options, the unknown options, the source bindings and each
/// component manifest. It removes the output directory only after all of these checks pass.
fn compose_dev_distribution(mut options: cli::Options, root: &trace::Span) -> Result<()> {
    let spec_file = required_path(&mut options, "--composition-spec")?;
    let spec = spec::read_composition_spec(&spec_file)?;
    let output_dir = required_path(&mut options, "--output-dir")?;
    let ide_config = required_path(&mut options, "--ide-config")?;
    let fingerprint_file = required_path(&mut options, "--fingerprint")?;
    let core_classpath_file = optional_path(&mut options, "--core-classpath-file")?;
    let plugin_classpath_file = optional_path(&mut options, "--plugin-classpath-file")?;
    let home = home_directory(&output_dir, options.take("--home-prefix")?.as_deref())?;
    options.finish()?;
    root.tag("componentCount", spec.components.len());

    let mut bindings = match (&spec.source_runfiles, &spec.source_bindings) {
        (Some(_), Some(_)) => bail!("Local launch metadata must not expand source bindings"),
        (Some(_), None) => None,
        (None, Some(bindings)) => Some(spec::read_source_bindings(bindings, &spec.components)?),
        // The Starlark caller always gives source bindings to a full distribution, so the composer needs no other
        // source.
        (None, None) => {
            bail!(
                "The composition spec {} requests a full distribution without source bindings, \
                 and the composer does not support that",
                spec_file.display()
            );
        }
    };
    let mut components = Vec::with_capacity(spec.components.len());
    for entry in &spec.components {
        // A component resolves the paths of its entries against the working directory. That is the execution root
        // where its action staged them, so nothing becomes absolute here for it.
        let manifest_file = paths::absolute_path(&entry.manifest)?;
        let mut component = DevBuildComponent::new(manifest::read_component_manifest(&manifest_file)?);
        if let Some(part) = &entry.plugin_classpath_part {
            component.plugin_classpath_part = Some(paths::absolute_path(part)?);
        }
        if let Some(bindings) = &mut bindings {
            // `read_source_bindings` refuses a repeated manifest, so each component takes its own bindings.
            component.source_bindings = bindings.remove(&entry.manifest);
        }
        components.push(component);
    }
    if let Some(placement) = &spec.placement {
        let manifests: Vec<&manifest::ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
        placement::check_placement(&manifests, placement)?;
    }

    remove_output(&output_dir)?;
    let compose_options = ComposeOptions {
        plugin_classpath_prefix: match &spec.plugin_classpath_prefix {
            Some(prefix) => Some(paths::absolute_path(prefix)?),
            None => None,
        },
        expected_fragments: spec.expected_fragments,
        additional_modules: spec.additional_modules,
        bundled_modules: spec.bundled_modules,
        source_runfiles: match &spec.source_runfiles {
            Some(runfiles) => Some(compose::absolute_keys(runfiles)?),
            None => None,
        },
        source_directory_runfiles: Some(compose::absolute_keys(&spec.source_directory_runfiles)?),
    };
    let home = home.as_path();
    let result = compose::compose_components(&components, home, &compose_options, root)?;
    for (file, content) in [
        (
            home.join("core-classpath.txt"),
            classpath::core_classpath_text(&result.core_class_path),
        ),
        (home.join("fingerprint.txt"), result.fingerprint.clone()),
        (fingerprint_file, result.fingerprint.clone()),
    ] {
        fs::write(&file, content).with_context(|| file.display().to_string())?;
    }
    for (copy, name) in [
        (core_classpath_file, "core-classpath.txt"),
        (plugin_classpath_file, component::plugin_classpath::PLUGIN_CLASSPATH),
    ] {
        if let Some(copy) = copy {
            let file = home.join(paths::from_slash(name).as_ref());
            fs::copy(&file, &copy).with_context(|| format!("copy {} to {}", file.display(), copy.display()))?;
        }
    }
    ide_config::write_dev_ide_config(
        &ide_config,
        home,
        &result.main_class,
        &result.platform_prefix,
        &result.additional_modules,
        &result.bundled_modules,
    )
}

/// The home of the IDE: `output_dir`, or its child `prefix` when the option names one.
///
/// The prefix is one directory name, such as `Contents` for a macOS app. An empty value is an absent option.
fn home_directory(output_dir: &Path, prefix: Option<&str>) -> Result<PathBuf> {
    let Some(prefix) = prefix.filter(|prefix| !prefix.is_empty()) else {
        return Ok(output_dir.to_path_buf());
    };
    if prefix.contains('/') || distpath::validate_relative_path(prefix).is_err() {
        bail!("--home-prefix must be one directory name, not {prefix:?}");
    }
    Ok(output_dir.join(prefix))
}

/// Removes the output of an earlier run. Bazel creates the declared output directory before the action runs.
fn remove_output(output_dir: &Path) -> Result<()> {
    let removed = match fs::symlink_metadata(output_dir) {
        Ok(metadata) if metadata.is_dir() => fs::remove_dir_all(output_dir),
        Ok(_) => fs::remove_file(output_dir),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error),
    };
    removed.with_context(|| output_dir.display().to_string())
}
