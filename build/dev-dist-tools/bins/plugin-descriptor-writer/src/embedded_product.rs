// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--embedded-product` mode: the executor of `dev_dist_embedded_product_descriptor`.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt;
use std::path::PathBuf;

use anyhow::{Result, bail};

use crate::compose::{self, Composition};
use crate::descriptorxml;
use crate::structural::{self, Cache, ContentRequest};
use crate::{Jars, append_descriptor_jar, put_descriptor, read_text, report, seed_cache, write_output};

/// The declared inputs of `dev_dist_embedded_product_descriptor`.
///
/// The rule states no descriptor search scope. Every lookup reads the one cache that the declared files seed, so this
/// mode refuses `--module` as an unknown option.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct EmbeddedProductRequest {
    pub output: PathBuf,
    pub source: ProductSource,
    pub descriptors: BTreeMap<String, PathBuf>,
    pub descriptors_in_jar: BTreeMap<String, Vec<PathBuf>>,
    pub separate_jar: BTreeSet<String>,
}

/// The root element of a product descriptor: a declared file, or the composition that the flags state.
///
/// `TRANSITION(IJAI-955)`: the file goes when the generator states every product as flags.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum ProductSource {
    File(PathBuf),
    Composed(Composition),
}

impl ProductSource {
    /// Reads the file, or builds the element of the composition.
    fn element(&self) -> Result<descriptorxml::Element> {
        match self {
            Self::File(path) => descriptorxml::read(&read_text(path)?),
            Self::Composed(composition) => Ok(composition.element()),
        }
    }
}

/// Names the source in a failure.
impl fmt::Display for ProductSource {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::File(path) => write!(formatter, "{}", path.display()),
            Self::Composed(_) => formatter.write_str("the composition of the flags"),
        }
    }
}

pub(crate) fn run(options: cli::Options) -> i32 {
    let parsed = match parse_embedded_product_request(options) {
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
        main_module: parsed.source.to_string(),
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
    let cache = seed_cache(&parsed.descriptors, &parsed.descriptors_in_jar, &mut Jars::default())?;
    let mut element = parsed.source.element()?;
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
/// The request states exactly one of `--source` and the composition flags of [`compose`].
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
    let source = match (options.take("--source")?, Composition::take(options)?) {
        (Some(_), Some(_)) => bail!("--source and the composition flags {} are exclusive", compose::FLAGS.join(", ")),
        (Some(file), None) if !file.is_empty() => ProductSource::File(file.into()),
        (None, Some(composition)) => ProductSource::Composed(composition),
        (_, None) => bail!("--source is required, or the composition flags {}", compose::FLAGS.join(", ")),
    };
    Ok(EmbeddedProductRequest {
        output,
        source,
        descriptors,
        descriptors_in_jar,
        separate_jar: BTreeSet::new(),
    })
}

#[cfg(test)]
mod tests;
