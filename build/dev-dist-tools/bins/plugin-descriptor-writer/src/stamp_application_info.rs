// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--stamp-application-info` mode: the executor of `dev_dist_product_application_info`.

use anyhow::{Result, bail};

use crate::application_info::{Replacement, replace_markers};
use crate::{parse_replacement, read_text, report, write_output};

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

pub(crate) fn run(options: cli::Options) -> i32 {
    let parsed = match parse_stamp_application_info_request(options) {
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

pub(crate) fn parse_stamp_application_info_request(mut options: cli::Options) -> Result<StampApplicationInfoRequest> {
    let mut replacements = Vec::new();
    for value in options.take_all("--replacement")? {
        replacements.push(parse_replacement(&value, &replacements)?);
    }
    let request = StampApplicationInfoRequest {
        output: options.require("--out")?,
        source: options.require("--source")?,
        replacements,
    };
    options.finish()?;
    if request.replacements.is_empty() {
        bail!("--replacement is required, because a product without replacements has no application info action");
    }
    Ok(request)
}
