// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `product-files` renders the launch files of one product for one platform: `build.txt`, `bin/idea.properties`, the
//! vmoptions file and `bin/product-info.json`.
//!
//! The input is the launch model that the dev distribution plan generator writes for the product (`ProductLaunchModel`
//! in `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productInfo`). The model states the facts
//! of the product code. The tool reads the rest from the application info of the product and from `build.txt`, so an
//! edit of the version, the suffix or the release date changes no generated file. The files must be the files that the
//! production writers of the build scripts write, byte for byte. `DevDistProductLaunchModelTest` checks
//! that for every split product and every host platform.

mod model;
mod render;

use std::ffi::{OsStr, OsString};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::Context;
use appinfo::{ApplicationInfo, Replacement};

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

/// The options in the order that the required check reports them. The first [`REQUIRED_OPTIONS`] are required.
const OPTION_NAMES: [&str; 12] = [
    "--model",
    "--platform",
    "--application-info",
    "--build-number",
    "--build-date-seconds",
    "--opened-packages",
    "--idea-properties",
    "--build-txt-out",
    "--idea-properties-out",
    "--vmoptions-out",
    "--product-info-out",
    "--host-application-info",
];

const REQUIRED_OPTIONS: usize = 11;

/// The one option that a request can state more than once, as `KEY=VALUE`. It is not in [`OPTION_NAMES`].
const REPLACEMENT_OPTION: &str = "--replacement";

struct Options {
    model: PathBuf,
    platform: String,
    /// The `idea/<prefix>ApplicationInfo.xml` source of the product, before the marker replacement.
    application_info: PathBuf,
    /// `community/build.txt`. Its text without surrounding whitespace is the build number.
    build_number: PathBuf,
    /// The build date that the dev distribution pins. An EAP product without a release date takes it.
    build_date_seconds: i64,
    opened_packages: PathBuf,
    idea_properties: PathBuf,
    build_txt_out: PathBuf,
    idea_properties_out: PathBuf,
    vm_options_out: PathBuf,
    product_info_out: PathBuf,
    /// The application info of the host product of a frontend.
    host_application_info: Option<PathBuf>,
    /// `ProductProperties.appInfoXmlReplacements`, in their order.
    replacements: Vec<Replacement>,
}

/// Runs the tool and returns the exit code: 2 for a usage error, 1 for a render error.
fn run(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let options = match parse_options(args) {
        Ok(options) => options,
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            return 2;
        }
    };
    if let Err(error) = render_to_files(&options) {
        let _ = writeln!(errors, "ERROR: {error:#}");
        return 1;
    }
    let _ = writeln!(
        output,
        "Rendered the launch files of {} for {}",
        options.model.display(),
        options.platform
    );
    0
}

fn parse_options(args: impl IntoIterator<Item = OsString>) -> anyhow::Result<Options> {
    let form_error = |arg: &OsStr| {
        anyhow::anyhow!(
            "expected one of the options in the '--key=value' form, but got {:?}",
            arg.to_string_lossy()
        )
    };
    let mut values: [Option<OsString>; OPTION_NAMES.len()] = Default::default();
    let mut replacements = Vec::new();
    let mut parser = lexopt::Parser::from_args(args);
    while let Some(arg) = parser.next()? {
        let name = match arg {
            lexopt::Arg::Long(name) => format!("--{name}"),
            lexopt::Arg::Short(short) => return Err(form_error(OsStr::new(&format!("-{short}")))),
            lexopt::Arg::Value(value) => return Err(form_error(&value)),
        };
        let Some(value) = parser.optional_value() else {
            return Err(form_error(OsStr::new(&name)));
        };
        if name == REPLACEMENT_OPTION {
            replacements.push(parse_replacement(&value, &replacements)?);
            continue;
        }
        let Some(index) = OPTION_NAMES.iter().position(|known| *known == name) else {
            let mut arg = OsString::from(format!("{name}="));
            arg.push(&value);
            return Err(form_error(&arg));
        };
        if values[index].is_some() {
            anyhow::bail!("{name} must be specified at most once");
        }
        if value.is_empty() && index >= REQUIRED_OPTIONS {
            anyhow::bail!("{name} must not be empty");
        }
        values[index] = Some(value);
    }
    if let Some(index) = values[..REQUIRED_OPTIONS]
        .iter()
        .position(|value| value.as_ref().is_none_or(|value| value.is_empty()))
    {
        anyhow::bail!("{} is required", OPTION_NAMES[index]);
    }
    let [
        model,
        platform,
        application_info,
        build_number,
        build_date_seconds,
        opened_packages,
        idea_properties,
        build_txt_out,
        idea_properties_out,
        vm_options_out,
        product_info_out,
        host_application_info,
    ] = values;
    let required = |value: Option<OsString>| value.expect("the required check found every required option");
    let build_date_seconds = required(build_date_seconds);
    let Some(build_date_seconds) = build_date_seconds.to_str().and_then(|seconds| seconds.parse::<i64>().ok()) else {
        anyhow::bail!(
            "--build-date-seconds is not a number of seconds: {:?}",
            build_date_seconds.to_string_lossy()
        );
    };
    Ok(Options {
        model: required(model).into(),
        platform: required(platform).to_string_lossy().into_owned(),
        application_info: required(application_info).into(),
        build_number: required(build_number).into(),
        build_date_seconds,
        opened_packages: required(opened_packages).into(),
        idea_properties: required(idea_properties).into(),
        build_txt_out: required(build_txt_out).into(),
        idea_properties_out: required(idea_properties_out).into(),
        vm_options_out: required(vm_options_out).into(),
        product_info_out: required(product_info_out).into(),
        host_application_info: host_application_info.map(PathBuf::from),
        replacements,
    })
}

/// Parses one `--replacement=KEY=VALUE`. The key must be new and not empty. The value can be empty.
fn parse_replacement(value: &OsStr, stated: &[Replacement]) -> anyhow::Result<Replacement> {
    let text = value.to_string_lossy();
    let Some((key, replacement)) = text.split_once('=').filter(|(key, _)| !key.is_empty()) else {
        anyhow::bail!("a replacement is '<key>=<value>', and {text:?} is not");
    };
    if stated.iter().any(|stated| stated.key == key) {
        anyhow::bail!("the replacement {key:?} is stated more than once");
    }
    Ok(Replacement::new(key, replacement))
}

fn read_text(file: &Path) -> anyhow::Result<String> {
    std::fs::read_to_string(file).with_context(|| format!("cannot read {}", file.display()))
}

/// Reads the build number of `build.txt`, without surrounding whitespace. An empty build number fails.
fn read_build_number(file: &Path) -> anyhow::Result<String> {
    let build_number = read_text(file)?.trim().to_owned();
    if build_number.is_empty() {
        anyhow::bail!("the build number is empty: {}", file.display());
    }
    Ok(build_number)
}

/// Reads the facts of the application info: the markers first, then the XML. A frontend reads its host too.
fn read_application_info(options: &Options) -> anyhow::Result<ApplicationInfo> {
    let file = options.application_info.display().to_string();
    let content = appinfo::replace_markers(&read_text(&options.application_info)?, &options.replacements);
    match &options.host_application_info {
        Some(host) => {
            let host_content = read_text(host)?;
            ApplicationInfo::read_frontend(
                &content,
                &file,
                &host_content,
                &host.display().to_string(),
                options.build_date_seconds,
            )
        }
        None => ApplicationInfo::read(&content, &file, options.build_date_seconds),
    }
}

fn render_to_files(options: &Options) -> anyhow::Result<()> {
    let model_text = std::fs::read(&options.model).with_context(|| format!("cannot read {}", options.model.display()))?;
    let model = model::parse_launch_model(&model_text).with_context(|| options.model.display().to_string())?;
    let target = render::parse_platform(&options.platform)?;
    let application_info = read_application_info(options)?;
    let build_number = read_build_number(&options.build_number)?;
    let opened_packages = read_text(&options.opened_packages)?;
    // The base file that the model names by `languageServerBase`. The caller passes it, so an action reads only its
    // own base file.
    let idea_properties = read_text(&options.idea_properties)?;
    let product = render::Product {
        model: &model,
        application_info: &application_info,
        build_number: &build_number,
    };
    let files = render::render_launch_files(&product, target, &opened_packages, &idea_properties)
        .with_context(|| options.model.display().to_string())?;
    for (path, content) in [
        (&options.build_txt_out, &files.build_txt),
        (&options.idea_properties_out, &files.idea_properties),
        (&options.vm_options_out, &files.vm_options),
        (&options.product_info_out, &files.product_info),
    ] {
        std::fs::write(path, content).with_context(|| format!("cannot write {}", path.display()))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests;
