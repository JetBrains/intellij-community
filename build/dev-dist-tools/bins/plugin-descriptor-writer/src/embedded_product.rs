// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--embedded-product` mode: the executor of `dev_dist_embedded_product_descriptor`.

use std::collections::{BTreeMap, BTreeSet};

use anyhow::{Result, bail};

use crate::descriptorxml;
use crate::structural::{self, Cache, ContentRequest};
use crate::{
    Mode, OptionLine, append_descriptor_jar, assign, is_mode_line, put_descriptor, read_text, refuse_repeated_options, report,
    require_mode, require_options, seed_cache, write_output,
};

/// The declared inputs of `dev_dist_embedded_product_descriptor`.
///
/// The rule also states `--module`, the descriptor search scope of the platform. It decides nothing here, because
/// every lookup reads the one cache that the declared files seed. So this mode accepts the option and reads nothing
/// from it.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct EmbeddedProductRequest {
    pub output: String,
    pub source: String,
    pub descriptors: BTreeMap<String, String>,
    pub descriptors_in_jar: BTreeMap<String, Vec<String>>,
    pub separate_jar: BTreeSet<String>,
}

pub(crate) fn run(lines: &[OptionLine]) -> i32 {
    let parsed = match parse_embedded_product_request(lines) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match resolve_embedded_product(&parsed) {
        Ok(content) => content,
        Err(error) => {
            let error = error.context(format!("could not resolve the embedded product descriptor ({})", parsed.source));
            return report(1, &error);
        }
    };
    match write_output(&parsed.output, content.as_bytes()) {
        Ok(()) => 0,
        Err(error) => report(1, &error),
    }
}

pub(crate) fn resolve_embedded_product(parsed: &EmbeddedProductRequest) -> Result<String> {
    let request = ContentRequest {
        main_module: parsed.source.clone(),
        separate_jar: parsed.separate_jar.clone(),
        embeds: true,
        ..ContentRequest::default()
    };
    Ok(resolve_product_content(parsed, &request)?.text)
}

/// A resolved product descriptor, and the descriptor cache that resolved it.
pub(crate) struct ProductContent {
    pub text: String,
    pub cache: Cache,
}

/// Resolves the includes of a product descriptor and embeds its content modules.
///
/// The embedded product descriptor and the product descriptor share this body. Only the content request differs.
pub(crate) fn resolve_product_content(parsed: &EmbeddedProductRequest, request: &ContentRequest) -> Result<ProductContent> {
    let cache = seed_cache(&parsed.descriptors, &parsed.descriptors_in_jar)?;
    let source = read_text(&parsed.source)?;
    let mut element = descriptorxml::read(&source)?;
    structural::resolve_includes(&mut element, &cache)?;
    structural::embed_content_modules(&mut element, request, &cache)?;
    Ok(ProductContent {
        text: descriptorxml::write(&element),
        cache,
    })
}

pub(crate) fn parse_embedded_product_request(lines: &[OptionLine]) -> Result<EmbeddedProductRequest> {
    require_mode(lines, Mode::EmbeddedProduct)?;
    refuse_repeated_options(lines, &["--descriptor", "--descriptor-in-jar", "--module", "--separate-jar"])?;
    let mut parsed = EmbeddedProductRequest::default();
    for line in lines {
        if is_mode_line(line, Mode::EmbeddedProduct) || parse_product_content_option(&mut parsed, line)? {
            continue;
        }
        match line.name.as_str() {
            "--module" => {
                line.value()?;
            }
            "--separate-jar" => {
                parsed.separate_jar.insert(line.value()?.to_owned());
            }
            option => bail!("unknown embedded product descriptor option '{option}'"),
        }
    }
    check_product_content_request(&parsed)?;
    Ok(parsed)
}

/// Reads an option that both product descriptor modes accept. It returns false for another option.
pub(crate) fn parse_product_content_option(parsed: &mut EmbeddedProductRequest, line: &OptionLine) -> Result<bool> {
    match line.name.as_str() {
        "--out" => assign(&mut parsed.output, line)?,
        "--source" => assign(&mut parsed.source, line)?,
        "--descriptor" => put_descriptor(&mut parsed.descriptors, line.value()?)?,
        "--descriptor-in-jar" => append_descriptor_jar(&mut parsed.descriptors_in_jar, line.value()?)?,
        _ => return Ok(false),
    }
    Ok(true)
}

pub(crate) fn check_product_content_request(parsed: &EmbeddedProductRequest) -> Result<()> {
    require_options(&[("--out", &parsed.output), ("--source", &parsed.source)])
}
