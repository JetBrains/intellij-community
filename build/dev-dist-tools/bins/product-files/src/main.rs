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

use std::ffi::OsString;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::Context;
use appinfo::{ApplicationInfo, Replacement};

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

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
            cli::report(errors, &error);
            return 2;
        }
    };
    if let Err(error) = render_to_files(&options) {
        cli::report(errors, &error);
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

/// Reads the options. Every option but `--host-application-info` and `--replacement` is required, and the first
/// missing one fails in this order. `--replacement` is the one option that a request can state more than once, as
/// `KEY=VALUE`.
fn parse_options(args: impl IntoIterator<Item = OsString>) -> anyhow::Result<Options> {
    let mut options = cli::parse(args)?;
    let model = options.require("--model")?.into();
    let platform = options.require("--platform")?;
    let application_info = options.require("--application-info")?.into();
    let build_number = options.require("--build-number")?.into();
    let build_date_seconds = options.require("--build-date-seconds")?;
    let opened_packages = options.require("--opened-packages")?.into();
    let idea_properties = options.require("--idea-properties")?.into();
    let build_txt_out = options.require("--build-txt-out")?.into();
    let idea_properties_out = options.require("--idea-properties-out")?.into();
    let vm_options_out = options.require("--vmoptions-out")?.into();
    let product_info_out = options.require("--product-info-out")?.into();
    let host_application_info = match options.take("--host-application-info")? {
        Some(value) if value.is_empty() => anyhow::bail!("--host-application-info must not be empty"),
        value => value.map(PathBuf::from),
    };
    let replacements = Replacement::parse_all(&options.take_all("--replacement")?)?;
    options.finish()?;
    let Ok(build_date_seconds) = build_date_seconds.parse::<i64>() else {
        anyhow::bail!("--build-date-seconds is not a number of seconds: {build_date_seconds:?}");
    };
    Ok(Options {
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
        replacements,
    })
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

fn render_to_files(options: &Options) -> anyhow::Result<()> {
    let model_text = std::fs::read(&options.model).with_context(|| format!("cannot read {}", options.model.display()))?;
    let model = model::parse_launch_model(&model_text).with_context(|| options.model.display().to_string())?;
    let target = render::parse_platform(&options.platform)?;
    let application_info = ApplicationInfo::load(
        &options.application_info,
        &options.replacements,
        options.host_application_info.as_deref(),
        options.build_date_seconds,
    )?;
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
