// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `product-files` renders the launch files of one product for one platform: `build.txt`, `bin/idea.properties`, the
//! vmoptions file and `bin/product-info.json`.
//!
//! The input is the launch model that the dev distribution plan generator writes for the product (`ProductLaunchModel`
//! in `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productInfo`). The files must be the
//! files that the production writers of the build scripts write, byte for byte. `DevDistProductLaunchModelTest` checks
//! that for every split product and every host platform.

mod model;
mod render;

use std::ffi::{OsStr, OsString};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use anyhow::Context;

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), &mut std::io::stdout(), &mut std::io::stderr()))
}

/// The options in the order that the required check reports them.
const OPTION_NAMES: [&str; 8] = [
    "--model",
    "--platform",
    "--opened-packages",
    "--idea-properties",
    "--build-txt-out",
    "--idea-properties-out",
    "--vmoptions-out",
    "--product-info-out",
];

struct Options {
    model: PathBuf,
    platform: String,
    opened_packages: PathBuf,
    idea_properties: PathBuf,
    build_txt_out: PathBuf,
    idea_properties_out: PathBuf,
    vm_options_out: PathBuf,
    product_info_out: PathBuf,
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
        let Some(index) = OPTION_NAMES.iter().position(|known| *known == name) else {
            let mut arg = OsString::from(format!("{name}="));
            arg.push(&value);
            return Err(form_error(&arg));
        };
        if values[index].is_some() {
            anyhow::bail!("{name} must be specified at most once");
        }
        values[index] = Some(value);
    }
    if let Some(index) = values.iter().position(|value| value.as_ref().is_none_or(|value| value.is_empty())) {
        anyhow::bail!("{} is required", OPTION_NAMES[index]);
    }
    let [
        model,
        platform,
        opened_packages,
        idea_properties,
        build_txt_out,
        idea_properties_out,
        vm_options_out,
        product_info_out,
    ] = values.map(|value| value.expect("the required check found every option"));
    Ok(Options {
        model: model.into(),
        platform: platform.to_string_lossy().into_owned(),
        opened_packages: opened_packages.into(),
        idea_properties: idea_properties.into(),
        build_txt_out: build_txt_out.into(),
        idea_properties_out: idea_properties_out.into(),
        vm_options_out: vm_options_out.into(),
        product_info_out: product_info_out.into(),
    })
}

fn read_text(file: &Path) -> anyhow::Result<String> {
    std::fs::read_to_string(file).with_context(|| format!("cannot read {}", file.display()))
}

fn render_to_files(options: &Options) -> anyhow::Result<()> {
    let model_text = std::fs::read(&options.model).with_context(|| format!("cannot read {}", options.model.display()))?;
    let model = model::parse_launch_model(&model_text).with_context(|| options.model.display().to_string())?;
    let target = render::parse_platform(&options.platform)?;
    let opened_packages = read_text(&options.opened_packages)?;
    // The base file that the model names by `languageServerBase`. The caller passes it, so an action reads only its
    // own base file.
    let idea_properties = read_text(&options.idea_properties)?;
    let files = render::render_launch_files(&model, target, &opened_packages, &idea_properties)
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
