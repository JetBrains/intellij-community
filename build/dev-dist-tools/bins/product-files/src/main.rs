// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `product-files` renders the launch files of one product for one platform: `build.txt`, `bin/idea.properties`, the
//! vmoptions file, `product-info.json` and, for a product with an embedded frontend, the vmoptions file of the frontend.
//! On macOS, a product whose model states a `macBundle` also gets `Info.plist` and the `InfoPlist.strings` files of its app.
//!
//! The input is the launch model that the distribution plan generator writes for the product (`ProductLaunchModel`
//! in `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productInfo`). The model states the facts
//! of the product code. The tool reads the rest from the application info of the product and from `build.txt`, so an
//! edit of the version, the suffix or the release date changes no generated file. A classic-loader launch also reads the
//! core classpath of the distribution for its boot class path. The files must be the files that the production writers
//! of the build scripts write, byte for byte. `DistProductLaunchModelTest` checks that for every split product and
//! every host platform.

mod mac;
mod model;
mod render;

use std::ffi::OsString;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::Context;
use appinfo::{ApplicationInfo, Replacement};

fn main() -> ExitCode {
    cli::main(run)
}

struct Options {
    model: PathBuf,
    platform: String,
    /// The `idea/<prefix>ApplicationInfo.xml` source of the product, before the marker replacement.
    application_info: PathBuf,
    /// `community/build.txt`. Its text without surrounding whitespace is the build number.
    build_number: PathBuf,
    /// The build date that the distribution pins. An EAP product without a release date takes it.
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
    /// The files of the macOS app template, each below a `Contents` directory. See [`mac::template_relative_path`].
    mac_bundle_template: Vec<PathBuf>,
    /// The `Contents` directory that gets the rendered template files. It is required on macOS when the model states
    /// `macBundle`, and refused otherwise.
    mac_bundle_out: Option<PathBuf>,
    /// The icon of each `Contents/Resources/<name>` of a release build, keyed by the name.
    mac_bundle_icons: Vec<(String, PathBuf)>,
    /// The icon of each `Contents/Resources/<name>` of an EAP build, keyed by the name.
    mac_bundle_eap_icons: Vec<(String, PathBuf)>,
}

/// Reads each `<name>=<path>` value of `option`.
fn parse_icons(option: &str, values: Vec<String>) -> anyhow::Result<Vec<(String, PathBuf)>> {
    values
        .into_iter()
        .map(|value| match value.split_once('=') {
            Some((name, path)) if !name.is_empty() && !path.is_empty() => Ok((name.to_owned(), PathBuf::from(path))),
            _ => anyhow::bail!("{option} is not <name>=<path>: {value:?}"),
        })
        .collect()
}

/// Runs the tool and returns the exit code: 2 for a usage error, 1 for a render error. A run that succeeds writes nothing.
fn run(args: impl IntoIterator<Item = OsString>, errors: &mut dyn Write) -> u8 {
    let Some(options) = cli::ok_or_report(errors, parse_options(args)) else {
        return 2;
    };
    match cli::ok_or_report(errors, render_to_files(&options)) {
        Some(()) => 0,
        None => 1,
    }
}

/// Reads the options. Every option but `--frontend-vmoptions-out`, `--host-application-info`,
/// `--core-classpath-jar-names`, `--replacement`, `--mac-bundle-template` and `--mac-bundle-out` is required, and the
/// first missing one fails in this order. A request can state `--replacement` and `--mac-bundle-template` more than once.
/// A replacement is `KEY=VALUE`.
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
    let mac_bundle_template: Vec<PathBuf> = options.take_all("--mac-bundle-template")?.into_iter().map(PathBuf::from).collect();
    let mac_bundle_out = match options.take("--mac-bundle-out")? {
        Some(value) if value.is_empty() => anyhow::bail!("--mac-bundle-out must not be empty"),
        value => value.map(PathBuf::from),
    };
    let mac_bundle_icons = parse_icons("--mac-bundle-icon", options.take_all("--mac-bundle-icon")?)?;
    let mac_bundle_eap_icons = parse_icons("--mac-bundle-eap-icon", options.take_all("--mac-bundle-eap-icon")?)?;
    options.finish()?;
    if mac_bundle_out.is_some() == mac_bundle_template.is_empty() {
        anyhow::bail!("--mac-bundle-out and --mac-bundle-template go together");
    }
    if mac_bundle_out.is_none() && !(mac_bundle_icons.is_empty() && mac_bundle_eap_icons.is_empty()) {
        anyhow::bail!("--mac-bundle-icon and --mac-bundle-eap-icon need --mac-bundle-out");
    }
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
        mac_bundle_template,
        mac_bundle_out,
        mac_bundle_icons,
        mac_bundle_eap_icons,
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
    let build_date = appinfo::format_build_date_time(options.build_date_seconds);
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
        build_date: &build_date,
        core_class_path: core_class_path.as_deref(),
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
    let (mac_bundle, mac_icons) = match (&model.mac_bundle, target.os == render::OS_MAC, &options.mac_bundle_out) {
        (Some(bundle), true, Some(directory)) => {
            let template = read_mac_template(&options.mac_bundle_template)?;
            let files = mac::render_mac_bundle(&product, bundle, target, options.build_date_seconds, &template)
                .with_context(|| options.model.display().to_string())?;
            let icons = mac::select_icons(
                &options.mac_bundle_icons,
                &options.mac_bundle_eap_icons,
                application_info.is_eap,
                &model.base_file_name,
            )?;
            (
                files
                    .into_iter()
                    .map(|(relative_path, content)| (directory.join(relative_path), content))
                    .collect(),
                icons
                    .into_iter()
                    .map(|(name, source)| (directory.join("Resources").join(name), source.to_path_buf()))
                    .collect(),
            )
        }
        (None, _, None) | (Some(_), false, None) => (Vec::new(), Vec::new()),
        (Some(_), true, None) => anyhow::bail!(
            "{} states macBundle, so --mac-bundle-out is required on {}",
            options.model.display(),
            options.platform
        ),
        (None, _, Some(_)) => anyhow::bail!("--mac-bundle-out is set, but {} states no macBundle", options.model.display()),
        (Some(_), false, Some(_)) => anyhow::bail!("--mac-bundle-out is set, but {} is not a macOS platform", options.platform),
    };
    for (path, content) in outputs {
        std::fs::write(path, content).with_context(|| format!("cannot write {}", path.display()))?;
    }
    for (path, content) in &mac_bundle {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).with_context(|| format!("cannot create {}", parent.display()))?;
        }
        std::fs::write(path, content).with_context(|| format!("cannot write {}", path.display()))?;
    }
    for (path, source) in &mac_icons {
        let bytes = std::fs::read(source).with_context(|| format!("cannot read {}", source.display()))?;
        std::fs::write(path, bytes).with_context(|| format!("cannot write {}", path.display()))?;
    }
    Ok(())
}

/// Reads each template file with its path below `Contents`.
fn read_mac_template(files: &[PathBuf]) -> anyhow::Result<Vec<mac::TemplateFile>> {
    files
        .iter()
        .map(|file| {
            Ok(mac::TemplateFile {
                relative_path: mac::template_relative_path(&file.display().to_string())?,
                text: read_text(file)?,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests;
