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
/// options. The rule states no build number either, so this mode refuses `--build-number` too.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct ApplicationInfoRequest {
    pub output: String,
    pub client_application_info: String,
    pub product_application_info: String,
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

/// Applies the client markers, then copies the names, the version and the release date of the product.
pub(crate) fn resolve_application_info(parsed: &ApplicationInfoRequest) -> Result<String> {
    let client_content = read_text(&parsed.client_application_info)?;
    let replaced = replace_markers(&client_content, &application_info_replacements());
    let product_content = read_text(&parsed.product_application_info)?;
    let product = ApplicationInfoElements::parse(&product_content, &parsed.product_application_info)?;
    let mut client = ApplicationInfoElements::parse(&replaced, &parsed.client_application_info)?;
    merge_host_application_info(&mut client, &product, &parsed.product_application_info)?;
    Ok(descriptorxml::write(&client.root))
}

/// The replacement map of `computeAppInfoXml` for the client template of a dev distribution.
///
/// A dev distribution stamps no build number and no build date. The run time reads the build number from `build.txt`.
/// A dev distribution has no artifact server, so `BUILTIN_PLUGINS_URL` is empty.
pub(crate) fn application_info_replacements() -> [Replacement; 1] {
    [Replacement::new("BUILTIN_PLUGINS_URL", "")]
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
            option => bail!("unknown frontend application info option '{option}'"),
        };
        assign(slot, line)?;
    }
    require_options(&[
        ("--out", &parsed.output),
        ("--client-application-info", &parsed.client_application_info),
        ("--product-application-info", &parsed.product_application_info),
    ])?;
    Ok(parsed)
}
