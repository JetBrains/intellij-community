// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `product-files` renders the launch files of one product for one platform: `build.txt`, `bin/idea.properties`, the
//! vmoptions file, `product-info.json` and, for a product with an embedded frontend, the vmoptions file of the frontend.
//!
//! The input is the launch model that the dev distribution plan generator writes for the product (`ProductLaunchModel`
//! in `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productInfo`). The model states the facts
//! of the product code. The tool reads the rest from the application info of the product and from `build.txt`, so an
//! edit of the version, the suffix or the release date changes no generated file. A classic-loader launch also reads the
//! core classpath of the distribution for its boot class path. The files must be the files that the production writers
//! of the build scripts write, byte for byte. `DevDistProductLaunchModelTest` checks that for every split product and
//! every host platform. That test passes no dev JVM argument, because production states none.

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
    /// `community/bin/common.vmoptions`: the vmoptions lines of every product.
    common_vm_options: PathBuf,
    build_txt_out: PathBuf,
    idea_properties_out: PathBuf,
    vm_options_out: PathBuf,
    product_info_out: PathBuf,
    /// The vmoptions file of the embedded frontend. It is required when the model states `frontendVmOptions`, and refused
    /// otherwise.
    frontend_vm_options_out: Option<PathBuf>,
    /// The application info of the host product of a frontend.
    host_application_info: Option<PathBuf>,
    /// The `lib/` jars of the core classpath, one name per line. It is required when the launch of the model states no
    /// `bootClassPathJarNames`, and refused otherwise.
    core_class_path_jar_names: Option<PathBuf>,
    /// `ProductProperties.appInfoXmlReplacements`, in their order.
    replacements: Vec<Replacement>,
    /// The JVM arguments that mark a dev distribution, in their order. See [`render::Product::dev_jvm_arguments`].
    dev_jvm_arguments: Vec<String>,
}

/// Runs the tool and returns the exit code: 2 for a usage error, 1 for a render error.
fn run(args: impl IntoIterator<Item = OsString>, output: &mut dyn Write, errors: &mut dyn Write) -> u8 {
    let Some(options) = cli::ok_or_report(errors, parse_options(args)) else {
        return 2;
    };
    if cli::ok_or_report(errors, render_to_files(&options)).is_none() {
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

/// Reads the options. Every option but `--frontend-vmoptions-out`, `--host-application-info`,
/// `--core-classpath-jar-names`, `--replacement` and `--dev-jvm-argument` is required, and the first missing one fails
/// in this order. A request can state `--replacement` and `--dev-jvm-argument` more than once. A replacement is
/// `KEY=VALUE`, and a dev JVM argument must not be empty.
fn parse_options(args: impl IntoIterator<Item = OsString>) -> anyhow::Result<Options> {
    let mut options = cli::parse(args)?;
    let model = options.require("--model")?.into();
    let platform = options.require("--platform")?;
    let application_info = options.require("--application-info")?.into();
    let build_number = options.require("--build-number")?.into();
    let build_date_seconds = options.require("--build-date-seconds")?;
    let opened_packages = options.require("--opened-packages")?.into();
    let idea_properties = options.require("--idea-properties")?.into();
    let common_vm_options = options.require("--common-vmoptions")?.into();
    let build_txt_out = options.require("--build-txt-out")?.into();
    let idea_properties_out = options.require("--idea-properties-out")?.into();
    let vm_options_out = options.require("--vmoptions-out")?.into();
    let product_info_out = options.require("--product-info-out")?.into();
    let frontend_vm_options_out = match options.take("--frontend-vmoptions-out")? {
        Some(value) if value.is_empty() => anyhow::bail!("--frontend-vmoptions-out must not be empty"),
        value => value.map(PathBuf::from),
    };
    let host_application_info = match options.take("--host-application-info")? {
        Some(value) if value.is_empty() => anyhow::bail!("--host-application-info must not be empty"),
        value => value.map(PathBuf::from),
    };
    let core_class_path_jar_names = match options.take("--core-classpath-jar-names")? {
        Some(value) if value.is_empty() => anyhow::bail!("--core-classpath-jar-names must not be empty"),
        value => value.map(PathBuf::from),
    };
    let replacements = Replacement::parse_all(&options.take_all("--replacement")?)?;
    let dev_jvm_arguments = options.take_all("--dev-jvm-argument")?;
    if dev_jvm_arguments.iter().any(String::is_empty) {
        anyhow::bail!("--dev-jvm-argument must not be empty");
    }
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
        common_vm_options,
        build_txt_out,
        idea_properties_out,
        vm_options_out,
        product_info_out,
        frontend_vm_options_out,
        host_application_info,
        core_class_path_jar_names,
        replacements,
        dev_jvm_arguments,
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

/// Reads the core classpath file: one `lib/`-relative jar name per line. An empty line and an empty list fail.
fn read_core_class_path(file: &Path) -> anyhow::Result<Vec<String>> {
    let text = read_text(file)?;
    let names: Vec<String> = text.lines().map(str::to_owned).collect();
    if names.is_empty() {
        anyhow::bail!("the core classpath names no jar: {}", file.display());
    }
    if names.iter().any(String::is_empty) {
        anyhow::bail!("the core classpath has an empty line: {}", file.display());
    }
    Ok(names)
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
    let common_vm_options = read_text(&options.common_vm_options)?;
    let core_class_path = match (&options.core_class_path_jar_names, &model.launch.boot_class_path_jar_names) {
        (Some(file), None) => Some(read_core_class_path(file)?),
        (None, Some(_)) => None,
        (None, None) => anyhow::bail!(
            "{} states no bootClassPathJarNames of the launch, so --core-classpath-jar-names is required",
            options.model.display()
        ),
        (Some(_), Some(_)) => anyhow::bail!(
            "--core-classpath-jar-names is set, but {} states the bootClassPathJarNames of the launch",
            options.model.display()
        ),
    };
    let product = render::Product {
        model: &model,
        application_info: &application_info,
        build_number: &build_number,
        core_class_path: core_class_path.as_deref(),
        dev_jvm_arguments: &options.dev_jvm_arguments,
    };
    let files = render::render_launch_files(&product, target, &opened_packages, &idea_properties, &common_vm_options)
        .with_context(|| options.model.display().to_string())?;
    let mut outputs = vec![
        (&options.build_txt_out, &files.build_txt),
        (&options.idea_properties_out, &files.idea_properties),
        (&options.vm_options_out, &files.vm_options),
        (&options.product_info_out, &files.product_info),
    ];
    match (&options.frontend_vm_options_out, &files.frontend_vm_options) {
        (Some(path), Some(content)) => outputs.push((path, content)),
        (None, None) => {}
        (None, Some(_)) => anyhow::bail!(
            "{} states frontendVmOptions, so --frontend-vmoptions-out is required",
            options.model.display()
        ),
        (Some(_), None) => anyhow::bail!(
            "--frontend-vmoptions-out is set, but {} states no frontendVmOptions",
            options.model.display()
        ),
    }
    for (path, content) in outputs {
        std::fs::write(path, content).with_context(|| format!("cannot write {}", path.display()))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests;
