//! `dev-dist-collector` writes the manifest of one dev-distribution component. The manifest names each file where
//! Bazel already put it. The collector copies nothing, and it reads a payload only to hash a file without metadata.
//!
//! Three modes exist. `intellij_dev_packed_jars_component` (`intellij_dev_dist.bzl`) uses `--jars-file` with
//! `--metadata-catalogue` and `--files-file`. `_dev_plugin` and `dev_plugin_component` use `--plugin-component`.
//!
//! [`inventory`] builds the manifest of each mode. [`plugin_classpath`] gives and checks the classpath record of a plugin
//! component.

mod collect;
mod inventory;
mod plugin_classpath;
mod plugin_component;

use std::ffi::OsString;
use std::io::Write;
use std::path::Path;

use anyhow::{Context, bail};

use crate::inventory::{ManifestHeader, SourcedFile};
use crate::plugin_component::{Outputs, PluginComponentSpec};

/// The mode of a run. `Spec` is the path of the plugin component spec after the option parse, and the checked spec
/// after [`Options::read_spec`].
#[derive(Debug)]
enum Mode<Spec> {
    Jars { jars_file: String, catalogue: String },
    Files { files_file: String },
    PluginComponent { spec: Spec, classpath: String },
}

impl<Spec> Mode<Spec> {
    /// The name of the root span and the noun of the success line.
    const fn job(&self) -> (&'static str, &'static str) {
        match self {
            Self::Jars { .. } => ("collect packed jars", "packed jars"),
            Self::Files { .. } => ("collect files", "files"),
            Self::PluginComponent { .. } => ("collect plugin component metadata", "plugin files"),
        }
    }
}

#[derive(Debug)]
struct Options<Spec> {
    manifest: String,
    kind: String,
    platform_prefix: String,
    /// Empty for a platform-neutral component, with `arch`.
    os: String,
    arch: String,
    mode: Mode<Spec>,
    trace_file: Option<String>,
    /// The IDE main class that the manifest declares.
    main_class: Option<String>,
}

impl Options<String> {
    /// Reads and checks the plugin component spec. `run` calls this before the tracer starts, so an invalid spec writes
    /// no span file.
    fn read_spec(self) -> anyhow::Result<Options<PluginComponentSpec>> {
        let mode = match self.mode {
            Mode::Jars { jars_file, catalogue } => Mode::Jars { jars_file, catalogue },
            Mode::Files { files_file } => Mode::Files { files_file },
            Mode::PluginComponent { spec, classpath } => {
                let outputs = Outputs {
                    manifest: &self.manifest,
                    classpath: &classpath,
                    trace_file: self.trace_file.as_deref(),
                };
                let spec = PluginComponentSpec::read(&spec, &outputs)?;
                Mode::PluginComponent { spec, classpath }
            }
        };
        Ok(Options {
            manifest: self.manifest,
            kind: self.kind,
            platform_prefix: self.platform_prefix,
            os: self.os,
            arch: self.arch,
            mode,
            trace_file: self.trace_file,
            main_class: self.main_class,
        })
    }
}

impl<Spec> Options<Spec> {
    fn header(&self) -> ManifestHeader {
        ManifestHeader {
            kind: self.kind.clone(),
            platform_prefix: self.platform_prefix.clone(),
            os: self.os.clone(),
            arch: self.arch.clone(),
            main_class: self.main_class.clone(),
            plugin_component: matches!(self.mode, Mode::PluginComponent { .. }),
        }
    }
}

fn main() -> std::process::ExitCode {
    std::process::ExitCode::from(run(
        std::env::args_os().skip(1).collect(),
        &mut std::io::stdout(),
        &mut std::io::stderr(),
    ))
}

/// Runs the tool and returns the exit code: 2 for an option error, 1 for any other error.
fn run(args: Vec<OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let options = match parse_options(args) {
        Ok(options) => options,
        Err(error) => {
            cli::report(errors, &error);
            return 2;
        }
    };
    let options = match options.read_spec() {
        Ok(options) => options,
        Err(error) => {
            cli::report(errors, &error);
            return 1;
        }
    };
    let (job, content) = options.mode.job();
    trace::run_traced(job, options.trace_file.as_deref().map(Path::new), 1, errors, |tracer, errors| {
        let root = tracer.span(job);
        root.tag("kind", options.kind.as_str());
        if matches!(options.mode, Mode::PluginComponent { .. }) {
            root.tag("byteCount", 0i64);
        }
        let result = collect(&options, &root);
        if let Err(error) = &result {
            root.fail(&format_args!("{error:#}"));
        }
        root.end();
        match result {
            Ok(count) => {
                let _ = writeln!(
                    output,
                    "Dev distribution component '{}' named {count} {content} in {}",
                    options.kind, options.manifest
                );
                0
            }
            Err(error) => {
                cli::report(errors, &error);
                1
            }
        }
    })
}

/// Collects the files of the mode and writes the manifest. The plugin component mode also writes the classpath record.
/// It returns the number of files that the manifest names.
fn collect(options: &Options<PluginComponentSpec>, root: &trace::Span) -> anyhow::Result<usize> {
    let (files, classpath) = match &options.mode {
        Mode::Jars { jars_file, catalogue } => {
            let span = root.child("collect platform jars");
            let result = collect::platform_jars(jars_file)
                .and_then(|files| Ok(inventory::attach_metadata(&files, Path::new(catalogue))?))
                .and_then(|files| collect::validate_destinations(&files).map(|()| files));
            (record_collection(&span, "jarCount", result)?, None)
        }
        Mode::Files { files_file } => {
            let span = root.child("collect explicit files");
            let result = collect::explicit_files(files_file).and_then(|files| collect::validate_destinations(&files).map(|()| files));
            (record_collection(&span, "fileCount", result)?, None)
        }
        Mode::PluginComponent { spec, classpath } => {
            let (files, record) = spec.collect(root)?;
            (files, Some((classpath, record)))
        }
    };
    write_manifest(options, &files, root)?;
    if let Some((classpath_part, record)) = classpath {
        let classpath_part = Path::new(classpath_part);
        if let Some(parent) = classpath_part.parent().filter(|parent| !parent.as_os_str().is_empty()) {
            std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
        }
        std::fs::write(classpath_part, record).with_context(|| format!("write {}", classpath_part.display()))?;
    }
    Ok(files.len())
}

fn record_collection(
    span: &trace::Span,
    count_name: &'static str,
    result: anyhow::Result<Vec<SourcedFile>>,
) -> anyhow::Result<Vec<SourcedFile>> {
    match &result {
        Ok(files) => {
            span.tag(count_name, files.len());
            span.tag("byteCount", 0i64);
        }
        Err(error) => span.fail(&format_args!("{error:#}")),
    }
    result
}

fn write_manifest(options: &Options<PluginComponentSpec>, files: &[SourcedFile], root: &trace::Span) -> anyhow::Result<()> {
    let span = root.child("inventory dev build component");
    match inventory::write_manifest(Path::new(&options.manifest), &options.header(), files) {
        Ok(stats) => {
            span.tag("fileCount", stats.file_count);
            span.tag("hashedFileCount", stats.hashed_file_count);
            span.tag("byteCount", stats.byte_count);
            Ok(())
        }
        Err(error) => {
            span.fail(&error);
            Err(error.into())
        }
    }
}

/// The value of an option. An empty value is an absent option.
fn value(options: &mut cli::Options, name: &str) -> anyhow::Result<Option<String>> {
    Ok(options.take(name)?.filter(|value| !value.is_empty()))
}

fn parse_options(args: Vec<OsString>) -> anyhow::Result<Options<String>> {
    let mut options = cli::parse(args)?;
    let manifest = options.require("--component-manifest")?;
    let kind = options.require("--kind")?;
    let platform_prefix = options.require("--platform-prefix")?;
    let os = value(&mut options, "--os")?;
    let arch = value(&mut options, "--arch")?;
    let platform_neutral = options.flag("--platform-neutral")?;
    let jars_file = value(&mut options, "--jars-file")?;
    let files_file = value(&mut options, "--files-file")?;
    let plugin_component = value(&mut options, "--plugin-component")?;
    let catalogue = value(&mut options, "--metadata-catalogue")?;
    let mut plugin_classpath = value(&mut options, "--plugin-classpath-part")?;
    let trace_file = value(&mut options, "--trace-file")?;
    let main_class = value(&mut options, "--main-class")?;
    options.finish()?;
    let mode = match (jars_file, files_file, plugin_component) {
        (Some(jars_file), None, None) => {
            let Some(catalogue) = catalogue else {
                bail!("packed jars require --metadata-catalogue; payload inventories belong to the packing action");
            };
            Mode::Jars { jars_file, catalogue }
        }
        (None, Some(files_file), None) => {
            if catalogue.is_some() {
                bail!("--files-file cannot use --metadata-catalogue: no rule passes metadata for explicit files");
            }
            Mode::Files { files_file }
        }
        (None, None, Some(spec)) => {
            if catalogue.is_some() {
                bail!("--plugin-component cannot use --metadata-catalogue");
            }
            if main_class.is_some() {
                bail!("--plugin-component cannot declare --main-class");
            }
            let Some(classpath) = plugin_classpath.take() else {
                bail!("--plugin-component and --plugin-classpath-part are required together");
            };
            Mode::PluginComponent { spec, classpath }
        }
        _ => bail!("exactly one of --jars-file, --files-file and --plugin-component is required"),
    };
    if plugin_classpath.is_some() {
        bail!("--plugin-component and --plugin-classpath-part are required together");
    }
    let (os, arch) = if platform_neutral {
        if !matches!(mode, Mode::PluginComponent { .. }) {
            bail!("--platform-neutral applies only to --plugin-component: no rule collects neutral jars or files");
        }
        if os.is_some() || arch.is_some() {
            bail!("--platform-neutral cannot be combined with --os or --arch");
        }
        (String::new(), String::new())
    } else {
        (target_os(os.as_deref())?, target_arch(arch.as_deref())?)
    };
    Ok(Options {
        manifest,
        kind,
        platform_prefix,
        os,
        arch,
        mode,
        trace_file,
        main_class,
    })
}

/// The OS of the manifest. The rules pass the OS of a `HOST_PLATFORMS` entry, with `macos` for `darwin`.
fn target_os(value: Option<&str>) -> anyhow::Result<String> {
    let os = match value {
        None => match std::env::consts::OS {
            "macos" => "mac",
            "linux" => "linux",
            "windows" => "windows",
            host => bail!("unsupported host OS {host:?}, expected one of windows, macos, linux"),
        },
        Some("macos") => "mac",
        Some("linux") => "linux",
        Some("windows") => "windows",
        Some(other) => bail!("unknown --os value {other:?}, expected one of windows, macos, linux"),
    };
    Ok(os.to_owned())
}

/// The architecture of the manifest. The rules pass the architecture of a `HOST_PLATFORMS` entry.
fn target_arch(value: Option<&str>) -> anyhow::Result<String> {
    let arch = match value {
        None => match std::env::consts::ARCH {
            "x86_64" => "x64",
            "aarch64" => "aarch64",
            host => bail!("unsupported host architecture {host:?}, expected one of x64, aarch64"),
        },
        Some("x64") => "x64",
        Some("aarch64") => "aarch64",
        Some(other) => bail!("unknown --arch value {other:?}, expected one of x64, aarch64"),
    };
    Ok(arch.to_owned())
}

#[cfg(test)]
mod test_support;

#[cfg(test)]
mod main_tests;

#[cfg(test)]
mod collect_tests;

#[cfg(test)]
mod plugin_component_tests;
