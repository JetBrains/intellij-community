//! `dev-dist-collector` writes the manifest of one dev-distribution component. The manifest names each file where
//! Bazel already put it. The collector copies nothing, and it reads a payload only to hash a file without metadata.
//!
//! Three modes exist. `intellij_dev_packed_jars_component` (`intellij_dev_dist.bzl`) uses `--jars-file` with
//! `--metadata-catalogue` and `--files-file`. `_dev_plugin` and `dev_plugin_component` use `--plugin-component`.
//! The subcommand `local-home` links a local home for `PreBuiltDevMain`.

mod collect;
mod plugin_component;

use std::ffi::{OsStr, OsString};
use std::io::Write;
use std::path::Path;

use anyhow::{Context, bail};
use component::inventory::{self, ManifestHeader, SourcedFile};
use tracing::field::Empty;

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
    if args.first().is_some_and(|arg| arg == "local-home") {
        return run_local_home(&args[1..], output, errors);
    }
    let options = match parse_options(args) {
        Ok(options) => options,
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            return 2;
        }
    };
    let options = match options.read_spec() {
        Ok(options) => options,
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            return 1;
        }
    };
    let (job, content) = options.mode.job();
    let tracer = options.trace_file.as_ref().map(|_| trace::Tracer::new(job));
    let dispatch = tracer.as_ref().map_or_else(tracing::Dispatch::none, trace::Tracer::dispatch);
    let result = tracing::dispatcher::with_default(&dispatch, || {
        let root = match &options.mode {
            Mode::PluginComponent { .. } => tracing::info_span!("collect", otel.name = job, kind = %options.kind, byteCount = 0i64),
            Mode::Jars { .. } | Mode::Files { .. } => tracing::info_span!("collect", otel.name = job, kind = %options.kind),
        };
        let result = collect(&options, &root);
        if let Err(error) = &result {
            trace::fail(&root, &format_args!("{error:#}"));
        }
        result
    });
    let code = match result {
        Ok(count) => {
            let _ = writeln!(
                output,
                "Dev distribution component '{}' named {count} {content} in {}",
                options.kind, options.manifest
            );
            0
        }
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            1
        }
    };
    if let (Some(tracer), Some(trace_file)) = (&tracer, &options.trace_file)
        && let Err(error) = tracer.write_file(Path::new(trace_file))
    {
        let _ = writeln!(errors, "ERROR: writing the span file: {error}");
        return 1;
    }
    code
}

/// Collects the files of the mode and writes the manifest. The plugin component mode also writes the classpath record.
/// It returns the number of files that the manifest names.
fn collect(options: &Options<PluginComponentSpec>, root: &tracing::Span) -> anyhow::Result<usize> {
    let (files, classpath) = match &options.mode {
        Mode::Jars { jars_file, catalogue } => {
            let span = tracing::info_span!(parent: root, "collect platform jars", jarCount = Empty, byteCount = Empty);
            let result = collect::platform_jars(jars_file)
                .and_then(|files| Ok(inventory::attach_metadata(&files, Path::new(catalogue))?))
                .and_then(|files| collect::validate_destinations(&files).map(|()| files));
            (record_collection(&span, "jarCount", result)?, None)
        }
        Mode::Files { files_file } => {
            let span = tracing::info_span!(parent: root, "collect explicit files", fileCount = Empty, byteCount = Empty);
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

fn record_collection(span: &tracing::Span, count_name: &str, result: anyhow::Result<Vec<SourcedFile>>) -> anyhow::Result<Vec<SourcedFile>> {
    match &result {
        Ok(files) => {
            span.record(count_name, trace::count(files.len()));
            span.record("byteCount", 0i64);
        }
        Err(error) => trace::fail(span, &format_args!("{error:#}")),
    }
    result
}

fn write_manifest(options: &Options<PluginComponentSpec>, files: &[SourcedFile], root: &tracing::Span) -> anyhow::Result<()> {
    let span = tracing::info_span!(
        parent: root,
        "inventory dev build component",
        fileCount = Empty,
        hashedFileCount = Empty,
        byteCount = Empty
    );
    match inventory::write_manifest(Path::new(&options.manifest), &options.header(), files) {
        Ok(stats) => {
            span.record("fileCount", trace::count(stats.file_count));
            span.record("hashedFileCount", trace::count(stats.hashed_file_count));
            span.record("byteCount", trace::count(stats.byte_count));
            Ok(())
        }
        Err(error) => {
            trace::fail(&span, &error);
            Err(error.into())
        }
    }
}

/// The values of the options, each given at most once.
#[derive(Default)]
struct Values {
    manifest: Option<String>,
    kind: Option<String>,
    platform_prefix: Option<String>,
    os: Option<String>,
    arch: Option<String>,
    jars_file: Option<String>,
    files_file: Option<String>,
    trace_file: Option<String>,
    catalogue: Option<String>,
    plugin_component: Option<String>,
    plugin_classpath: Option<String>,
    main_class: Option<String>,
    platform_neutral: bool,
}

fn parse_options(args: Vec<OsString>) -> anyhow::Result<Options<String>> {
    let form_error = |arg: &OsStr| anyhow::anyhow!("expected an option in the '--key=value' form, but got {:?}", arg.to_string_lossy());
    let mut values = Values::default();
    let mut seen = std::collections::HashSet::new();
    let mut parser = lexopt::Parser::from_args(args);
    while let Some(arg) = parser.next()? {
        let name = match arg {
            lexopt::Arg::Long(name) => format!("--{name}"),
            lexopt::Arg::Short(short) => return Err(form_error(OsStr::new(&format!("-{short}")))),
            lexopt::Arg::Value(value) => return Err(form_error(&value)),
        };
        let value = parser.optional_value();
        if !seen.insert(name.clone()) {
            bail!("{name} must be specified at most once");
        }
        if name == "--platform-neutral" {
            if value.is_some() {
                bail!("--platform-neutral takes no value");
            }
            values.platform_neutral = true;
            continue;
        }
        let destination = match name.as_str() {
            "--component-manifest" => &mut values.manifest,
            "--kind" => &mut values.kind,
            "--platform-prefix" => &mut values.platform_prefix,
            "--os" => &mut values.os,
            "--arch" => &mut values.arch,
            "--jars-file" => &mut values.jars_file,
            "--files-file" => &mut values.files_file,
            "--trace-file" => &mut values.trace_file,
            "--metadata-catalogue" => &mut values.catalogue,
            "--plugin-component" => &mut values.plugin_component,
            "--plugin-classpath-part" => &mut values.plugin_classpath,
            "--main-class" => &mut values.main_class,
            _ => bail!("unknown option: {name}"),
        };
        let Some(value) = value else {
            bail!("{name} requires a value in the '--key=value' form");
        };
        let value = value
            .into_string()
            .map_err(|value| anyhow::anyhow!("{name} is not valid UTF-8: {}", value.display()))?;
        // An empty value is an absent option.
        *destination = Some(value).filter(|value| !value.is_empty());
    }
    let required = |value: Option<String>, name: &str| value.with_context(|| format!("{name} is required"));
    let manifest = required(values.manifest, "--component-manifest")?;
    let kind = required(values.kind, "--kind")?;
    let platform_prefix = required(values.platform_prefix, "--platform-prefix")?;
    let mode = match (values.jars_file, values.files_file, values.plugin_component) {
        (Some(jars_file), None, None) => {
            let Some(catalogue) = values.catalogue else {
                bail!("packed jars require --metadata-catalogue; payload inventories belong to the packing action");
            };
            Mode::Jars { jars_file, catalogue }
        }
        (None, Some(files_file), None) => {
            if values.catalogue.is_some() {
                bail!("--files-file cannot use --metadata-catalogue: no rule passes metadata for explicit files");
            }
            Mode::Files { files_file }
        }
        (None, None, Some(spec)) => {
            if values.catalogue.is_some() {
                bail!("--plugin-component cannot use --metadata-catalogue");
            }
            if values.main_class.is_some() {
                bail!("--plugin-component cannot declare --main-class");
            }
            let Some(classpath) = values.plugin_classpath.take() else {
                bail!("--plugin-component and --plugin-classpath-part are required together");
            };
            Mode::PluginComponent { spec, classpath }
        }
        _ => bail!("exactly one of --jars-file, --files-file and --plugin-component is required"),
    };
    if values.plugin_classpath.is_some() {
        bail!("--plugin-component and --plugin-classpath-part are required together");
    }
    let (os, arch) = if values.platform_neutral {
        if !matches!(mode, Mode::PluginComponent { .. }) {
            bail!("--platform-neutral applies only to --plugin-component: no rule collects neutral jars or files");
        }
        if values.os.is_some() || values.arch.is_some() {
            bail!("--platform-neutral cannot be combined with --os or --arch");
        }
        (String::new(), String::new())
    } else {
        (target_os(values.os.as_deref())?, target_arch(values.arch.as_deref())?)
    };
    Ok(Options {
        manifest,
        kind,
        platform_prefix,
        os,
        arch,
        mode,
        trace_file: values.trace_file,
        main_class: values.main_class,
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

/// `local-home --layout=<file> --output-dir=<directory>`: links the local home of `PreBuiltDevMain`.
fn run_local_home(args: &[OsString], output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let mut layout = None;
    let mut output_dir = None;
    for arg in args {
        let parsed = arg
            .to_str()
            .and_then(|arg| arg.split_once('='))
            .filter(|(_, value)| !value.is_empty());
        let destination = match parsed {
            Some(("--layout", value)) if layout.is_none() => (&mut layout, value),
            Some(("--output-dir", value)) if output_dir.is_none() => (&mut output_dir, value),
            _ => {
                let _ = writeln!(errors, "ERROR: invalid local-home option: {}", arg.display());
                return 2;
            }
        };
        *destination.0 = Some(destination.1.to_owned());
    }
    let (Some(layout), Some(output_dir)) = (layout, output_dir) else {
        let _ = writeln!(errors, "ERROR: local-home requires --layout and --output-dir");
        return 2;
    };
    let env = component::local_home::RunfilesEnv::from_process();
    if let Err(error) = component::local_home::link_local_home(Path::new(&layout), Path::new(&output_dir), &env) {
        let _ = writeln!(errors, "ERROR: {error}");
        return 1;
    }
    let _ = writeln!(output, "Prepared the local dev home");
    0
}

#[cfg(test)]
mod test_support;

#[cfg(test)]
mod main_tests;

#[cfg(test)]
mod collect_tests;

#[cfg(test)]
mod plugin_component_tests;
