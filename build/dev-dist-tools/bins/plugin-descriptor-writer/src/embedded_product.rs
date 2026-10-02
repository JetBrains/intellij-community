// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--embedded-product` mode: the executor of `dev_dist_embedded_product_descriptor`.

use std::collections::{BTreeMap, BTreeSet};
use std::path::PathBuf;

use anyhow::{Result, anyhow};

use crate::compose::{self, Composition};
use crate::descriptorxml::{self, Element};
use crate::structural::{self, Cache, ContentRequest};
use crate::{Jars, append_descriptor_jar, put_descriptor, report, seed_cache, write_output};

/// The declared inputs of `dev_dist_embedded_product_descriptor`.
///
/// The rule states no descriptor search scope. Every lookup reads the one cache that the declared files seed, so this
/// mode refuses `--module` as an unknown option.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct EmbeddedProductRequest {
    pub output: PathBuf,
    /// The root element of the product descriptor, as the flags of [`compose`] state it.
    pub composition: Composition,
    pub descriptors: BTreeMap<String, PathBuf>,
    pub descriptors_in_jar: BTreeMap<String, Vec<PathBuf>>,
    pub separate_jar: BTreeSet<String>,
}

pub(crate) fn run(options: cli::Options) -> i32 {
    let parsed = match parse_embedded_product_request(options) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match resolve_embedded_product(&parsed) {
        Ok(content) => content,
        Err(error) => {
            let error = error.context(format!(
                "could not resolve the embedded product descriptor {}",
                parsed.output.display()
            ));
            return report(1, &error);
        }
    };
    match write_output(&parsed.output, content.as_bytes()) {
        Ok(()) => 0,
        Err(error) => report(1, &error),
    }
}

pub(crate) fn resolve_embedded_product(parsed: &EmbeddedProductRequest) -> Result<String> {
    Ok(resolve_product_content(parsed, &embedded_content_request(parsed))?.text)
}

/// The content request of the embedded product descriptor. The output names the descriptor in a failure, because the
/// rule states no module.
pub(crate) fn embedded_content_request(parsed: &EmbeddedProductRequest) -> ContentRequest {
    ContentRequest {
        main_module: parsed.output.display().to_string(),
        separate_jar: parsed.separate_jar.clone(),
        embeds: true,
        ..ContentRequest::default()
    }
}

/// A resolved product descriptor, and the descriptor cache that resolved it.
pub(crate) struct ProductContent {
    pub text: String,
    pub cache: Cache,
}

/// Composes the root element of a product descriptor, resolves its includes and embeds its content modules.
///
/// The embedded product descriptor and the product descriptor share this body. Only the content request differs.
pub(crate) fn resolve_product_content(parsed: &EmbeddedProductRequest, request: &ContentRequest) -> Result<ProductContent> {
    resolve_root(parsed.composition.element(), parsed, request)
}

/// Resolves the includes of `element` and embeds its content modules from the declared descriptors of `parsed`.
///
/// The Kotlin fixtures of the stages state roots that no composition states, so their tests call this directly.
pub(crate) fn resolve_root(mut element: Element, parsed: &EmbeddedProductRequest, request: &ContentRequest) -> Result<ProductContent> {
    let cache = seed_cache(&parsed.descriptors, &parsed.descriptors_in_jar, &mut Jars::default())?;
    structural::resolve_includes(&mut element, &cache)?;
    structural::embed_content_modules(&mut element, request, &cache)?;
    Ok(ProductContent {
        text: descriptorxml::write(&element),
        cache,
    })
}

pub(crate) fn parse_embedded_product_request(mut options: cli::Options) -> Result<EmbeddedProductRequest> {
    let mut parsed = parse_product_content(&mut options)?;
    parsed.separate_jar = options.take_all("--separate-jar")?.into_iter().collect();
    options.finish()?;
    Ok(parsed)
}

/// Takes the options that both product descriptor modes accept.
///
/// The request states at least one of the composition flags of [`compose`].
pub(crate) fn parse_product_content(options: &mut cli::Options) -> Result<EmbeddedProductRequest> {
    let mut descriptors = BTreeMap::new();
    for value in options.take_all("--descriptor")? {
        put_descriptor(&mut descriptors, &value)?;
    }
    let mut descriptors_in_jar = BTreeMap::new();
    for value in options.take_all("--descriptor-in-jar")? {
        append_descriptor_jar(&mut descriptors_in_jar, &value)?;
    }
    let output = options.require("--out")?.into();
    let composition = Composition::take(options)?
        .ok_or_else(|| anyhow!("at least one of the composition flags {} is required", compose::FLAGS.join(", ")))?;
    Ok(EmbeddedProductRequest {
        output,
        composition,
        descriptors,
        descriptors_in_jar,
        separate_jar: BTreeSet::new(),
    })
}

#[cfg(test)]
mod tests;
