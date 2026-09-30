// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! Writes the `META-INF/plugin.xml` that the main jar of a plugin receives.
//!
//! The `--embedded-product` mode resolves the includes and embeds the content modules, without plugin stamps.
//! The `--product-descriptor` mode does the same for the product descriptor of the application-info module.
//! The `--application-info` mode produces the application info of the embedded JetBrains Client.
//! The `--stamp-application-info` mode stamps the application info of a product.
//!
//! With no mode flag, it is the executor of the `dev_dist_plugin_descriptor` rule
//! (`community/platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl`), and the counterpart of
//! `applyPluginDescriptorPatch` of
//! `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt`. A descriptor feeds
//! every plugin main jar, so a JVM action for it would sit on the critical path of the build.
//!
//! Exit code 2 is a request that the rule cannot state. Exit code 1 is a request that the inputs cannot satisfy.
//!
//! ### The subset
//!
//! The port covers what the four descriptor rules state and what the declared descriptors hold. It refuses the rest
//! with an error that names the input. The request is one `--flagfile=<file>` argument, because every rule passes
//! its options that way. An option that no rule states is unknown here, and so is an option that the rules have but
//! no target sets. The reader of [`descriptorxml`], in the `appinfo` crate, lists the XML constructs that it refuses. A refusal turns a new
//! input into a build failure. So no code that the tests do not cover writes the bytes of that input.
//!
//! ### The stages
//!
//! The patch has seven stages, and this binary runs four of them:
//!
//! ```text
//! source → rawTextPatcher → reserialized → stamps → includes → contentModules → textPatcher
//! ```
//!
//! `rawTextPatcher` and `textPatcher` are Kotlin lambdas of a layout, so they are code and not data. The generated plan
//! holds out a plugin whose layout states one. A layout that states its raw patch as marker rows is the exception, and
//! [`markers`] applies the rows. `reserialized` is the round trip of [`descriptorxml`], `stamps` is [`stamps`], and
//! the two structural stages are [`structural`].
//!
//! ### The guards
//!
//! This binary is the one producer of the text. The curated cases of [`descriptorxml`], [`stamps`] and [`structural`]
//! are the committed gate, and the platform produced every expectation in them.
//! `//build:idea_dev_descriptor_leaf_build_test` builds a sample group of leaves. `./build/dev-dist.cmd snapshot diff`
//! compares every plugin main jar of a composed distribution against a recorded baseline.
//!
//! ### Windows
//!
//! Windows Installer Detection treats an exe whose name contains `patch`, `setup`, `install` or `update` as an
//! installer and asks for elevation. A non-elevated Bazel action then fails with `CreateProcess` error 740. This binary
//! embeds no manifest that turns the detection off, so its name is the guard. The name `plugin-descriptor-writer`
//! contains none of the four words, and a new name must also avoid them.

mod application_info;
mod embedded_product;
mod markers;
mod product_descriptor;
mod stamp_application_info;
mod stamps;
mod structural;

#[cfg(test)]
mod application_info_tests;
#[cfg(test)]
mod embedded_product_tests;
#[cfg(test)]
mod main_tests;
#[cfg(test)]
mod product_descriptor_tests;
#[cfg(test)]
mod rule_inputs_tests;
#[cfg(test)]
mod stamp_application_info_tests;
#[cfg(test)]
mod test_support;

use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::fs::File;
use std::io::{Cursor, Read};
use std::path::Path;

use anyhow::{Context, Result, anyhow, bail};
use appinfo::{ApplicationInfo, Replacement, descriptorxml};
use lexopt::{Arg, ValueExt};
use memmap2::Mmap;
use zip::ZipArchive;

use crate::stamps::CompatibleBuildRange;
use crate::structural::{Cache, ContentRequest};

fn main() {
    let mut arguments = Vec::new();
    for argument in std::env::args_os().skip(1) {
        match argument.into_string() {
            Ok(argument) => arguments.push(argument),
            Err(argument) => {
                eprintln!("ERROR: the argument {} is not valid UTF-8", argument.display());
                std::process::exit(2);
            }
        }
    }
    let code = run(&arguments);
    if code != 0 {
        std::process::exit(code);
    }
}

/// Runs one request and returns the exit code. The code is 0 for success and 2 for a request that the rule cannot
/// state. The code is 1 for a request that the inputs cannot satisfy.
fn run(arguments: &[String]) -> i32 {
    let lines = match read_request(arguments) {
        Ok(lines) => lines,
        Err(error) => return report(2, &error),
    };
    let mode = match select_operation(&lines) {
        Ok(mode) => mode,
        Err(error) => return report(2, &error),
    };
    match mode {
        Some(Mode::EmbeddedProduct) => embedded_product::run(&lines),
        Some(Mode::ProductDescriptor) => product_descriptor::run(&lines),
        Some(Mode::ApplicationInfo) => application_info::run(&lines),
        Some(Mode::StampApplicationInfo) => stamp_application_info::run(&lines),
        None => run_plugin_descriptor(&lines),
    }
}

fn report(code: i32, error: &anyhow::Error) -> i32 {
    eprintln!("ERROR: {error:#}");
    code
}

/// One option of the request: the name up to the first `=`, and the value after it. `None` is an option with no `=`,
/// which only a mode flag is.
#[derive(Clone, Debug, PartialEq, Eq)]
struct OptionLine {
    name: String,
    value: Option<String>,
}

impl OptionLine {
    /// Returns the value. Every option but a mode flag states one, because the rules write `--name=value`.
    fn value(&self) -> Result<&str> {
        self.value
            .as_deref()
            .ok_or_else(|| anyhow!("{} takes a value, as {}=<value>", self.name, self.name))
    }
}

/// Reads the request from the one argument that every rule passes, `--flagfile=<file>`.
///
/// `use_param_file("--flagfile=%s", use_always = True)` of the rules writes a multiline file, one option per line.
fn read_request(arguments: &[String]) -> Result<Vec<OptionLine>> {
    let [argument] = arguments else {
        bail!(
            "the request is one --flagfile=<file> argument, and {} arguments were passed",
            arguments.len()
        );
    };
    let Some(file) = argument.strip_prefix("--flagfile=") else {
        bail!("the request is one --flagfile=<file> argument, and '{argument}' is not one");
    };
    let content = read_text(file)?;
    parse_option_lines(content.lines().map(str::to_owned).collect())
}

/// Splits the lines of the flag file into [`OptionLine`]s.
///
/// A line is `--name=value` or `--name`. The value never comes from the next line. A short option, a bare value, an
/// empty line and a bare `--` are refused, because no rule writes one.
fn parse_option_lines(lines: Vec<String>) -> Result<Vec<OptionLine>> {
    // lexopt reads a bare `--` as the end of the options and reports nothing for it, so the check comes first.
    if lines.iter().any(|line| line == "--") {
        bail!("the flag file states a bare '--', which is not an option");
    }
    let mut parser = lexopt::Parser::from_args(lines);
    let mut result = Vec::new();
    while let Some(argument) = parser.next()? {
        let name = match argument {
            Arg::Long(name) => format!("--{name}"),
            Arg::Short(name) => bail!("the flag file states the short option '-{name}', which is not an option"),
            Arg::Value(value) => bail!("the flag file states the line '{}', which is not an option", value.string()?),
        };
        let value = parser.optional_value().map(ValueExt::string).transpose()?;
        result.push(OptionLine { name, value });
    }
    Ok(result)
}

fn assign(slot: &mut String, line: &OptionLine) -> Result<()> {
    line.value()?.clone_into(slot);
    Ok(())
}

/// Refuses an option that the request states twice, unless it is one of the list options. The rules state every other
/// option once, so a second one has no meaning to keep.
fn refuse_repeated_options(lines: &[OptionLine], list_options: &[&str]) -> Result<()> {
    let mut stated = HashSet::new();
    for line in lines {
        if !list_options.contains(&line.name.as_str()) && !stated.insert(line.name.as_str()) {
            bail!("{} is stated more than once", line.name);
        }
    }
    Ok(())
}

/// The four mode flags. A request with none of them patches a plugin descriptor.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Mode {
    EmbeddedProduct,
    ProductDescriptor,
    ApplicationInfo,
    StampApplicationInfo,
}

impl Mode {
    const ALL: [Self; 4] = [
        Self::EmbeddedProduct,
        Self::ProductDescriptor,
        Self::ApplicationInfo,
        Self::StampApplicationInfo,
    ];

    const fn flag(self) -> &'static str {
        match self {
            Self::EmbeddedProduct => "--embedded-product",
            Self::ProductDescriptor => "--product-descriptor",
            Self::ApplicationInfo => "--application-info",
            Self::StampApplicationInfo => "--stamp-application-info",
        }
    }
}

/// Returns the mode of the request. A request without a mode flag stays on the plugin patching path.
fn select_operation(lines: &[OptionLine]) -> Result<Option<Mode>> {
    let mut mode: Option<Mode> = None;
    for line in lines {
        let Some(found) = Mode::ALL.into_iter().find(|mode| mode.flag() == line.name) else {
            continue;
        };
        if line.value.is_some() {
            bail!("{} is a flag and takes no value", line.name);
        }
        if let Some(selected) = mode {
            bail!("only one mode flag is allowed, got {} and {}", selected.flag(), line.name);
        }
        mode = Some(found);
    }
    Ok(mode)
}

/// Fails unless the request selects exactly this mode.
fn require_mode(lines: &[OptionLine], want: Mode) -> Result<()> {
    if select_operation(lines)? != Some(want) {
        bail!("{} is required", want.flag());
    }
    Ok(())
}

/// Reports whether the line is the flag of this mode, which the parser of the mode skips.
fn is_mode_line(line: &OptionLine, mode: Mode) -> bool {
    line.name == mode.flag() && line.value.is_none()
}

/// Fails for the first option in the list whose value is empty.
fn require_options(required: &[(&str, &str)]) -> Result<()> {
    for (option, value) in required {
        if value.is_empty() {
            bail!("{option} is required");
        }
    }
    Ok(())
}

/// The request of one plugin, as the rule states it.
///
/// Every field is a string, a boolean, a path or a list of those. Nothing here can reach a layout or a build context.
/// `dev_dist_plugin_descriptor` states the request, and [`parse_plugin_request`] is its one reader.
#[derive(Debug, Default, PartialEq, Eq)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "each field is one switch of the request that the rule states"
)]
struct PluginRequest {
    output: String,
    main_module: String,
    source: String,
    source_entry: String,
    build_number_file: String,
    /// The application info of the product. The stamps take the EAP flag, the release date and the release version
    /// from it.
    application_info: String,
    /// The application info of the host product, for a frontend. Empty for every other product.
    host_application_info: String,
    /// The markers of the application info, in the order that the writer replaces them.
    replacements: Vec<Replacement>,
    /// The pinned build date. An EAP product without a release date takes its date.
    build_date_seconds: i64,
    exact_version: bool,
    retain_product: bool,
    embeds_content: bool,
    /// Finishes the ordinary XML normalization before the embedded descriptors add CDATA.
    reserialize_before_content_embedding: bool,
    /// The content modules that the filter of the product refuses. Normally empty.
    refused_content_modules: Vec<String>,
    /// The content modules whose body is embedded although `--embed-content-modules` is false: the modules the
    /// product's mode refuses at run time, whose jar the distribution does not place.
    embedded_content_modules: BTreeSet<String>,
    separate_jar: BTreeSet<String>,
    /// The descriptors that the patch can reach, keyed by load path.
    plugin_descriptors: BTreeMap<String, String>,
    /// The descriptors that no production source root holds, keyed by load path and valued by the jars of the library
    /// container that answers it. The load path is also the zip entry, because `toLoadPath` strips the leading `/`.
    /// The rule declares a container and not a jar, so the jar that holds the entry is found here.
    plugin_descriptors_in_jar: BTreeMap<String, Vec<String>>,
    /// The raw text patch of the layout as marker-table rows, in the order it applies them.
    markers: Vec<String>,
    /// What the layout appends to the IDE build version. Empty for a layout that stamps the version unchanged.
    version_suffix: String,
    /// Replaces the since and until constraint that the flags give.
    compatible_build_range: Option<CompatibleBuildRange>,
    /// When set, receives the final descriptor after one more [`descriptorxml`] round trip. That is the form that the
    /// plugin classpath record embeds (`generatePluginClassPathFromOrderedAssets` of `orderedAssets.kt`).
    reserialized_output: String,
}

fn run_plugin_descriptor(lines: &[OptionLine]) -> i32 {
    let parsed = match parse_plugin_request(lines) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match patch(&parsed) {
        Ok(content) => content,
        Err(error) => {
            let error = error.context(format!("could not patch the descriptor (module={})", parsed.main_module));
            return report(1, &error);
        }
    };
    let mut outputs = vec![(parsed.output.as_str(), content.clone())];
    if !parsed.reserialized_output.is_empty() {
        match reserialize(&content) {
            Ok(reserialized) => outputs.push((parsed.reserialized_output.as_str(), reserialized)),
            Err(error) => {
                let error = error.context(format!("could not reserialize the descriptor (module={})", parsed.main_module));
                return report(1, &error);
            }
        }
    }
    for (file, text) in outputs {
        if let Err(error) = write_output(file, text.as_bytes()) {
            return report(1, &error);
        }
    }
    0
}

/// The `JDOMUtil.load` and `JDOMUtil.write` pair that the classpath writer applies to a descriptor.
fn reserialize(content: &str) -> Result<String> {
    Ok(descriptorxml::write(&descriptorxml::read(content)?))
}

/// The port of `applyPluginDescriptorPatch` (`PluginXmlPatcher.kt`), the body that the assembly runs. The plan drives it
/// instead of the product layout.
fn patch(parsed: &PluginRequest) -> Result<String> {
    let build_number = read_text(&parsed.build_number_file)?.trim().to_owned();
    let application_info = read_application_info(parsed)?;
    let plugin_version = stamps::plugin_build_number(&build_number)? + &parsed.version_suffix;
    let compatible_build_range = parsed.compatible_build_range.unwrap_or(if parsed.exact_version {
        CompatibleBuildRange::Exact
    } else if application_info.is_eap {
        CompatibleBuildRange::RestrictedToSameRelease
    } else {
        CompatibleBuildRange::NewerWithSameBaseline
    });
    let (since_build, until_build) = stamps::compatible_platform_version_range(compatible_build_range, &build_number);

    let source = if parsed.source_entry.is_empty() {
        read_file(&parsed.source)?
    } else {
        read_first_zip_entry(std::slice::from_ref(&parsed.source), &parsed.source_entry)?
    };
    let source = String::from_utf8(source).with_context(|| format!("{} is not valid UTF-8", parsed.source))?;
    let patched = markers::apply(&source, &parsed.markers)?;
    let cache = seed_cache(&parsed.plugin_descriptors, &parsed.plugin_descriptors_in_jar)?;

    let mut element = descriptorxml::read(&patched)?;
    stamps::apply(
        &mut element,
        &stamps::Request {
            version: plugin_version,
            since_build,
            until_build,
            release_date: application_info.major_release_date.clone(),
            release_version: application_info.release_version_for_licensing(),
            retain_product_descriptor_for_bundled_plugin: parsed.retain_product,
            is_eap: application_info.is_eap,
        },
    );
    structural::resolve_includes(&mut element, &cache)?;
    if parsed.reserialize_before_content_embedding {
        element = descriptorxml::read(&descriptorxml::write(&element))?;
    }
    structural::embed_content_modules(
        &mut element,
        &ContentRequest {
            main_module: parsed.main_module.clone(),
            refused: parsed.refused_content_modules.clone(),
            separate_jar: parsed.separate_jar.clone(),
            embeds: parsed.embeds_content,
            embedded: parsed.embedded_content_modules.clone(),
            scrambled: BTreeSet::new(),
        },
        &cache,
    )?;
    // `patchText` is the identity here, for the reason of `rawTextPatcher`.
    Ok(descriptorxml::write(&element))
}

/// Reads the facts of the application info: the markers first, then the XML. A frontend reads its host too, as the
/// product files tool does.
fn read_application_info(parsed: &PluginRequest) -> Result<ApplicationInfo> {
    let content = appinfo::replace_markers(&read_text(&parsed.application_info)?, &parsed.replacements);
    if parsed.host_application_info.is_empty() {
        return ApplicationInfo::read(&content, &parsed.application_info, parsed.build_date_seconds);
    }
    let host_content = read_text(&parsed.host_application_info)?;
    ApplicationInfo::read_frontend(
        &content,
        &parsed.application_info,
        &host_content,
        &parsed.host_application_info,
        parsed.build_date_seconds,
    )
}

/// Reads one `--replacement=KEY=VALUE`. The key must be new and not empty. The value can be empty.
fn parse_replacement(value: &str, stated: &[Replacement]) -> Result<Replacement> {
    let Some((key, replacement)) = value.split_once('=').filter(|(key, _)| !key.is_empty()) else {
        bail!("a replacement is '<key>=<value>', and '{value}' is not");
    };
    if stated.iter().any(|stated| stated.key == key) {
        bail!("the replacement '{key}' is stated more than once");
    }
    Ok(Replacement::new(key, replacement))
}

/// Reads the parameter file that `dev_dist_plugin_descriptor` writes, one option per line.
///
/// An option that the parser does not know fails the run. That is the rule of the platform too. It keeps the rule and
/// this binary on one spelling: a rule that grows an option reaches this parser or fails here.
fn parse_plugin_request(lines: &[OptionLine]) -> Result<PluginRequest> {
    refuse_repeated_options(
        lines,
        &[
            "--marker",
            "--refused-content-module",
            "--embed-content-module",
            "--separate-jar",
            "--plugin-descriptor",
            "--plugin-descriptor-in-jar",
            "--replacement",
        ],
    )?;
    let mut parsed = PluginRequest {
        embeds_content: true,
        ..PluginRequest::default()
    };
    let mut build_date_seconds = None;
    for line in lines {
        match line.name.as_str() {
            "--out" => assign(&mut parsed.output, line)?,
            "--main-module" => assign(&mut parsed.main_module, line)?,
            "--source" => assign(&mut parsed.source, line)?,
            "--source-in-jar" => {
                let (entry, jar) = parse_descriptor_jar(line.value()?)?;
                entry.clone_into(&mut parsed.source_entry);
                jar.clone_into(&mut parsed.source);
            }
            "--build-number-file" => assign(&mut parsed.build_number_file, line)?,
            "--application-info-source" => assign(&mut parsed.application_info, line)?,
            "--host-application-info-source" => assign(&mut parsed.host_application_info, line)?,
            "--replacement" => {
                let replacement = parse_replacement(line.value()?, &parsed.replacements)?;
                parsed.replacements.push(replacement);
            }
            "--build-date-seconds" => {
                let value = line.value()?;
                let Ok(seconds) = value.parse::<i64>() else {
                    bail!("--build-date-seconds is not a number of seconds: '{value}'");
                };
                build_date_seconds = Some(seconds);
            }
            "--exact-version" => parsed.exact_version = parse_boolean_strict(line.value()?)?,
            "--retain-product-descriptor" => parsed.retain_product = parse_boolean_strict(line.value()?)?,
            "--embed-content-modules" => parsed.embeds_content = parse_boolean_strict(line.value()?)?,
            "--reserialize-before-content-embedding" => {
                parsed.reserialize_before_content_embedding = parse_boolean_strict(line.value()?)?;
            }
            "--refused-content-module" => parsed.refused_content_modules.push(line.value()?.to_owned()),
            "--embed-content-module" => {
                parsed.embedded_content_modules.insert(line.value()?.to_owned());
            }
            "--separate-jar" => {
                parsed.separate_jar.insert(line.value()?.to_owned());
            }
            "--plugin-descriptor" => put_descriptor(&mut parsed.plugin_descriptors, line.value()?)?,
            "--plugin-descriptor-in-jar" => {
                append_descriptor_jar(&mut parsed.plugin_descriptors_in_jar, line.value()?)?;
            }
            "--marker" => parsed.markers.push(line.value()?.to_owned()),
            "--version-suffix" => assign(&mut parsed.version_suffix, line)?,
            // The layouts state one range. `EXACT` and `RESTRICTED_TO_SAME_RELEASE` still come from the two flags.
            "--compatible-build-range" => match line.value()? {
                "NEWER_WITH_SAME_BASELINE" => {
                    parsed.compatible_build_range = Some(CompatibleBuildRange::NewerWithSameBaseline);
                }
                other => {
                    bail!("the compatible build range '{other}' is not supported, only NEWER_WITH_SAME_BASELINE is")
                }
            },
            "--reserialized-output" => assign(&mut parsed.reserialized_output, line)?,
            option => bail!("unknown option '{option}'"),
        }
    }
    if lines.iter().any(|line| line.name == "--source") && lines.iter().any(|line| line.name == "--source-in-jar") {
        bail!("the descriptor source is declared more than once");
    }
    // The rule states the application info of every product, so a request without it is one that the rule cannot state.
    require_options(&[
        ("--out", &parsed.output),
        ("--main-module", &parsed.main_module),
        ("--source", &parsed.source),
        ("--build-number-file", &parsed.build_number_file),
        ("--application-info-source", &parsed.application_info),
    ])?;
    parsed.build_date_seconds = build_date_seconds.context("--build-date-seconds is required")?;
    if lines.iter().any(|line| line.name == "--host-application-info-source") && parsed.host_application_info.is_empty() {
        bail!("--host-application-info-source must not be empty");
    }
    Ok(parsed)
}

/// Reads `<entry>=<jar>`.
fn parse_descriptor_jar(value: &str) -> Result<(&str, &str)> {
    match value.split_once('=') {
        Some((entry, jar)) if !entry.is_empty() && !jar.is_empty() => Ok((entry, jar)),
        _ => bail!("a jar descriptor is '<entry>=<jar>', and '{value}' is not"),
    }
}

/// Kotlin's `String.toBooleanStrict`, which accepts exactly `true` and `false`.
fn parse_boolean_strict(value: &str) -> Result<bool> {
    match value {
        "true" => Ok(true),
        "false" => Ok(false),
        _ => bail!("'{value}' is neither 'true' nor 'false'"),
    }
}

/// Records a declared file by its load path. The rules refuse a load path that two declarations answer, and so does
/// this function.
fn put_descriptor(into: &mut BTreeMap<String, String>, value: &str) -> Result<()> {
    let Some((load_path, file)) = value.split_once('=').filter(|(load_path, _)| !load_path.is_empty()) else {
        bail!("a descriptor is '<load path>=<file>', and '{value}' is not");
    };
    if into.insert(load_path.to_owned(), file.to_owned()).is_some() {
        bail!("the load path '{load_path}' is declared twice");
    }
    Ok(())
}

/// Records a jar candidate for a load path.
///
/// One option for each (load path, jar), because the rule declares a library container and states every jar of it.
/// The order is the order of the container, and the first jar that has the entry answers.
fn append_descriptor_jar(into: &mut BTreeMap<String, Vec<String>>, value: &str) -> Result<()> {
    match value.split_once('=') {
        Some((load_path, jar)) if !load_path.is_empty() => {
            into.entry(load_path.to_owned()).or_default().push(jar.to_owned());
            Ok(())
        }
        _ => bail!("a descriptor is '<load path>=<file>', and '{value}' is not"),
    }
}

/// Reads the declared descriptor files and the declared jar entries into one cache, keyed by load path.
///
/// The assembly uses `findFileInModuleLibraryDependencies` in `moduleContentUtil.kt` to check each library jar for the
/// load path. The rule declares the container. The writer checks its jars in order and uses the first matching entry.
/// When no jar has the entry, the action fails and names every jar it checked. A load path that both a file and a jar
/// answer fails, as the rules refuse it.
fn seed_cache(files: &BTreeMap<String, String>, jars: &BTreeMap<String, Vec<String>>) -> Result<Cache> {
    let mut cache = Cache::default();
    for (load_path, file) in files {
        cache.insert(load_path.clone(), read_file(file)?)?;
    }
    for (load_path, candidates) in jars {
        cache.insert(load_path.clone(), read_first_zip_entry(candidates, load_path)?)?;
    }
    Ok(cache)
}

/// Returns the entry of the first jar that has it.
fn read_first_zip_entry(jars: &[String], name: &str) -> Result<Vec<u8>> {
    for jar in jars {
        if let Some(data) = read_zip_entry(jar, name)? {
            return Ok(data);
        }
    }
    bail!("no declared jar has the entry '{name}': {}", jars.join(", "))
}

/// Returns the entry of the jar, or `None` when the jar has no entry of that name.
fn read_zip_entry(jar: &str, name: &str) -> Result<Option<Vec<u8>>> {
    let file = File::open(jar).with_context(|| format!("open {jar}"))?;
    let length = file.metadata().with_context(|| format!("open {jar}"))?.len();
    let map;
    let bytes: &[u8] = if length == 0 {
        &[]
    } else {
        // SAFETY: the jar is a declared input of the action, and no process changes it while the action runs.
        map = unsafe { Mmap::map(&file) }.with_context(|| format!("map {jar}"))?;
        &map
    };
    let mut archive = ZipArchive::new(Cursor::new(bytes)).map_err(|error| anyhow!("{jar}: not a valid zip file: {error}"))?;
    let Some(index) = archive.index_for_name(name) else {
        return Ok(None);
    };
    let mut entry = archive
        .by_index(index)
        .with_context(|| format!("{jar}: cannot open the entry '{name}'"))?;
    let mut data = Vec::with_capacity(usize::try_from(entry.size()).unwrap_or_default());
    entry
        .read_to_end(&mut data)
        .with_context(|| format!("{jar}: cannot read the entry '{name}'"))?;
    Ok(Some(data))
}

/// Reads a declared input. The error names the file.
fn read_file(path: &str) -> Result<Vec<u8>> {
    std::fs::read(path).with_context(|| format!("open {path}"))
}

/// Reads a declared text input. A file that is not UTF-8 fails, because the platform reads every input as UTF-8.
fn read_text(path: &str) -> Result<String> {
    String::from_utf8(read_file(path)?).with_context(|| format!("{path} is not valid UTF-8"))
}

/// Writes one output, and creates its directory first.
fn write_output(file: &str, content: &[u8]) -> Result<()> {
    let path = Path::new(file);
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        fscopy::create_dirs_0755(parent)?;
    }
    std::fs::write(path, content).with_context(|| format!("open {file}"))
}
