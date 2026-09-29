// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--application-info` mode: the executor of `dev_dist_frontend_application_info`.

use anyhow::{Result, bail};
use appinfo::{ApplicationInfoElements, descriptorxml, merge_host_application_info};

#[cfg(test)]
pub(crate) use appinfo::APPLICATION_INFO_NAMESPACE;
pub(crate) use appinfo::{Replacement, replace_markers};

use crate::{
    Mode, OptionLine, assign, is_mode_line, read_text, refuse_repeated_options, report, require_mode, require_options, write_output,
};

/// The declared inputs of `dev_dist_frontend_application_info`.
///
/// The rule states none of the overrides that `applyApplicationInfoOverrides` in `ApplicationInfoPropertiesImpl.kt`
/// applies. So this mode refuses `--eap-override`, `--version-suffix-override`, `--nightly` and `--branch-name` as unknown
/// options.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct ApplicationInfoRequest {
    pub output: String,
    pub client_application_info: String,
    pub product_application_info: String,
    pub build_number: String,
}

pub(crate) fn run(lines: &[OptionLine]) -> i32 {
    let parsed = match parse_application_info_request(lines) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match resolve_application_info(&parsed) {
        Ok(content) => content,
        Err(error) => return report(1, &error.context("could not produce the frontend application info")),
    };
    match write_output(&parsed.output, content.as_bytes()) {
        Ok(()) => 0,
        Err(error) => report(1, &error),
    }
}

/// Applies the client build markers, then copies the names, the version and the release date of the product.
pub(crate) fn resolve_application_info(parsed: &ApplicationInfoRequest) -> Result<String> {
    let build_number = read_build_number(&parsed.build_number, "frontend")?;
    let client_content = read_text(&parsed.client_application_info)?;
    let replaced = replace_markers(&client_content, &application_info_replacements(&[], "JBC", &build_number));
    let product_content = read_text(&parsed.product_application_info)?;
    let product = ApplicationInfoElements::parse(&product_content, &parsed.product_application_info)?;
    let mut client = ApplicationInfoElements::parse(&replaced, &parsed.client_application_info)?;
    merge_host_application_info(&mut client, &product, &parsed.product_application_info)?;
    Ok(descriptorxml::write(&client.root))
}

/// Reads the build number of the file, without surrounding whitespace. An empty build number fails.
pub(crate) fn read_build_number(file: &str, owner: &str) -> Result<String> {
    let build_number = read_text(file)?.trim().to_owned();
    if build_number.is_empty() {
        bail!("the {owner} build number is empty: {file}");
    }
    Ok(build_number)
}

/// The replacement map of `computeAppInfoXml` for a dev distribution.
///
/// The product replacements come first. A base key that a product replacement also states keeps that position and
/// takes the base value, as Kotlin's `Map.plus` does. A dev distribution stamps no `BUILD_DATE`, and it has no artifact
/// server, so `BUILTIN_PLUGINS_URL` is empty.
pub(crate) fn application_info_replacements(product: &[Replacement], product_code: &str, build_number: &str) -> Vec<Replacement> {
    let mut result = product.to_vec();
    for base in [
        Replacement::new("BUILD_NUMBER", &format!("{product_code}-{build_number}")),
        Replacement::new("BUILD", build_number),
        Replacement::new("BUILTIN_PLUGINS_URL", ""),
    ] {
        let mut stated = false;
        for replacement in result.iter_mut().filter(|replacement| replacement.key == base.key) {
            replacement.value.clone_from(&base.value);
            stated = true;
        }
        if !stated {
            result.push(base);
        }
    }
    result
}

pub(crate) fn parse_application_info_request(lines: &[OptionLine]) -> Result<ApplicationInfoRequest> {
    require_mode(lines, Mode::ApplicationInfo)?;
    refuse_repeated_options(lines, &[])?;
    let mut parsed = ApplicationInfoRequest::default();
    for line in lines {
        if is_mode_line(line, Mode::ApplicationInfo) {
            continue;
        }
        let slot = match line.name.as_str() {
            "--out" => &mut parsed.output,
            "--client-application-info" => &mut parsed.client_application_info,
            "--product-application-info" => &mut parsed.product_application_info,
            "--build-number" => &mut parsed.build_number,
            option => bail!("unknown frontend application info option '{option}'"),
        };
        assign(slot, line)?;
    }
    require_options(&[
        ("--out", &parsed.output),
        ("--client-application-info", &parsed.client_application_info),
        ("--product-application-info", &parsed.product_application_info),
        ("--build-number", &parsed.build_number),
    ])?;
    Ok(parsed)
}
