// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--stamp-application-info` mode: the executor of `dev_dist_product_application_info`.

use anyhow::{Result, bail};

use crate::application_info::{Replacement, replace_markers};
use crate::{
    Mode, OptionLine, assign, is_mode_line, read_text, refuse_repeated_options, report, require_mode, require_options, write_output,
};

/// The declared inputs of the application info of a product. The application-info module ships it as
/// `idea/<prefix>ApplicationInfo.xml`.
///
/// The rule states no build number and no product code. So this mode refuses `--build-number` and `--product-code` as
/// unknown options.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct StampApplicationInfoRequest {
    pub output: String,
    pub source: String,
    /// `ProductProperties.appInfoXmlReplacements`, in their order. The request states at least one.
    pub replacements: Vec<Replacement>,
}

pub(crate) fn run(lines: &[OptionLine]) -> i32 {
    let parsed = match parse_stamp_application_info_request(lines) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match stamp_application_info(&parsed) {
        Ok(content) => content,
        Err(error) => {
            let error = error.context(format!("could not replace the markers of the application info ({})", parsed.source));
            return report(1, &error);
        }
    };
    match write_output(&parsed.output, content.as_bytes()) {
        Ok(()) => 0,
        Err(error) => report(1, &error),
    }
}

/// `computeAppInfoXml` (`ApplicationInfoPropertiesImpl.kt`) for a dev distribution.
///
/// The mode replaces the markers of the product replacements and no other marker. `__BUILD__`, `__BUILD_NUMBER__` and
/// `__BUILD_DATE__` stay, and the run time reads the build number from `build.txt`. The markers are replaced in the
/// text, and the text keeps its comments and its layout. The platform loads and writes the text again only for
/// `applyApplicationInfoOverrides`, and the rule states no override.
pub(crate) fn stamp_application_info(parsed: &StampApplicationInfoRequest) -> Result<String> {
    let source = read_text(&parsed.source)?;
    Ok(replace_markers(&source, &parsed.replacements))
}

pub(crate) fn parse_stamp_application_info_request(lines: &[OptionLine]) -> Result<StampApplicationInfoRequest> {
    require_mode(lines, Mode::StampApplicationInfo)?;
    refuse_repeated_options(lines, &["--replacement"])?;
    let mut parsed = StampApplicationInfoRequest::default();
    for line in lines {
        if is_mode_line(line, Mode::StampApplicationInfo) {
            continue;
        }
        let slot = match line.name.as_str() {
            "--out" => &mut parsed.output,
            "--source" => &mut parsed.source,
            "--replacement" => {
                let value = line.value()?;
                let Some((key, text)) = value.split_once('=').filter(|(key, _)| !key.is_empty()) else {
                    bail!("a replacement is '<key>=<value>', and '{value}' is not");
                };
                if parsed.replacements.iter().any(|replacement| replacement.key == key) {
                    bail!("the replacement '{key}' is stated more than once");
                }
                parsed.replacements.push(Replacement::new(key, text));
                continue;
            }
            option => bail!("unknown application info stamp option '{option}'"),
        };
        assign(slot, line)?;
    }
    require_options(&[("--out", &parsed.output), ("--source", &parsed.source)])?;
    if parsed.replacements.is_empty() {
        bail!("--replacement is required, because a product without replacements has no application info action");
    }
    Ok(parsed)
}
