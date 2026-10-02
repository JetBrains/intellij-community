// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--product-descriptor` mode: the executor of `dev_dist_product_descriptor`.

use std::collections::BTreeSet;
use std::path::PathBuf;

use anyhow::{Context, Result};

use crate::descriptorxml;
use crate::embedded_product::{EmbeddedProductRequest, ProductContent, parse_product_content, resolve_product_content};
use crate::structural::{self, ContentRequest};
use crate::{report, write_output};

/// `PLUGIN_CLASSPATH_FORMAT_VERSION` of `classpath.kt`, the first byte of `plugins/plugin-classpath.txt`.
pub(crate) const PLUGIN_CLASS_PATH_FORMAT_VERSION: u8 = 3;

/// The declared inputs of the product descriptor, the `META-INF` descriptor of the application-info module.
///
/// The source is the Product DSL content with the module sets inlined. It is the text that
/// `processAndGetProductPluginContentModules` (`productModuleLayout.kt`) loads. The flags compose it, or a file that the
/// generator writes states it. The plan states the refusals of the content filter of the product and the scrambled
/// content modules. So the action loads no project model.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct ProductDescriptorRequest {
    pub content: EmbeddedProductRequest,
    pub main_module: String,
    pub refused: Vec<String>,
    pub scrambled: BTreeSet<String>,
    /// Receives the prefix of `plugins/plugin-classpath.txt`.
    pub plugin_class_path_prefix: PathBuf,
    /// Receives the descriptor of that prefix alone, with no header.
    pub classpath_descriptor: PathBuf,
}

pub(crate) fn run(options: cli::Options) -> i32 {
    let parsed = match parse_product_descriptor_request(options) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match resolve_product_descriptor(&parsed) {
        Ok(content) => content,
        Err(error) => {
            let error = error.context(format!("could not resolve the product descriptor (module={})", parsed.main_module));
            return report(1, &error);
        }
    };
    let descriptor = classpath_descriptor(&content, &parsed.main_module).and_then(|descriptor| {
        let prefix = plugin_class_path_prefix(&descriptor)?;
        Ok((descriptor, prefix))
    });
    let (descriptor, prefix) = match descriptor {
        Ok(result) => result,
        Err(error) => {
            let error = error.context(format!(
                "could not write the plugin classpath prefix (module={})",
                parsed.main_module
            ));
            return report(1, &error);
        }
    };
    let outputs = [
        (&parsed.content.output, content.text.into_bytes()),
        (&parsed.plugin_class_path_prefix, prefix),
        (&parsed.classpath_descriptor, descriptor.into_bytes()),
    ];
    for (file, content) in outputs {
        if let Err(error) = write_output(file, &content) {
            return report(1, &error);
        }
    }
    0
}

/// `writePluginClassPathPrefix` (`classpath.kt`): the format version, the size of the product descriptor as a
/// big-endian 32-bit integer, and the product descriptor.
pub(crate) fn plugin_class_path_prefix(descriptor: &str) -> Result<Vec<u8>> {
    let size = u32::try_from(descriptor.len()).context("the product descriptor does not fit a 32-bit size")?;
    let mut prefix = Vec::with_capacity(5 + descriptor.len());
    prefix.push(PLUGIN_CLASS_PATH_FORMAT_VERSION);
    prefix.extend_from_slice(&size.to_be_bytes());
    prefix.extend_from_slice(descriptor.as_bytes());
    Ok(prefix)
}

/// `createCachedProductDescriptor`. It loads the product descriptor, so every embedded body becomes text. Then it
/// embeds a descriptor into every `<module/>` that is still empty. So a scrambled module gets its descriptor here, and
/// no filter runs. The runtime module repository reads the same descriptor as the core plugin.
fn classpath_descriptor(content: &ProductContent, main_module: &str) -> Result<String> {
    let mut element = descriptorxml::read(&content.text)?;
    let request = ContentRequest {
        main_module: main_module.to_owned(),
        embeds: true,
        ..ContentRequest::default()
    };
    structural::embed_content_modules(&mut element, &request, &content.cache)?;
    Ok(descriptorxml::write(&element))
}

/// The part of `processAndGetProductPluginContentModules` (`productModuleLayout.kt`) that follows the load of the
/// source.
///
/// The includes resolve first. Then the content filter removes the refused modules, and every other content module
/// receives its descriptor, except a scrambled one. The product descriptor takes no `separate-jar` attribute, because
/// `processProductModule` embeds with no descriptor modifier.
pub(crate) fn resolve_product_descriptor(parsed: &ProductDescriptorRequest) -> Result<ProductContent> {
    let request = ContentRequest {
        main_module: parsed.main_module.clone(),
        refused: parsed.refused.clone(),
        scrambled: parsed.scrambled.clone(),
        embeds: true,
        ..ContentRequest::default()
    };
    resolve_product_content(&parsed.content, &request)
}

pub(crate) fn parse_product_descriptor_request(mut options: cli::Options) -> Result<ProductDescriptorRequest> {
    let request = ProductDescriptorRequest {
        content: parse_product_content(&mut options)?,
        refused: options.take_all("--refused-content-module")?,
        scrambled: options.take_all("--scrambled-content-module")?.into_iter().collect(),
        main_module: options.require("--main-module")?,
        plugin_class_path_prefix: options.require("--plugin-classpath-prefix")?.into(),
        classpath_descriptor: options.require("--classpath-descriptor")?.into(),
    };
    options.finish()?;
    Ok(request)
}

#[cfg(test)]
mod tests;
