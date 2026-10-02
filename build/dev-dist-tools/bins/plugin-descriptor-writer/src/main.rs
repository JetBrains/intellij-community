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
//! The two product modes compose the root element of the descriptor from the flags of [`compose`].
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
mod compose;
mod embedded_product;
mod markers;
mod product_descriptor;
mod stamp_application_info;
mod stamps;
mod structural;

#[cfg(test)]
mod test_support;
#[cfg(test)]
mod tests;

use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::ffi::OsString;
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, anyhow, bail};
use appinfo::{ApplicationInfo, Replacement, descriptorxml};
use jarpack::Jar;

use crate::stamps::CompatibleBuildRange;
use crate::structural::{Cache, ContentRequest};

fn main() {
    let code = run(std::env::args_os().skip(1));
    if code != 0 {
        std::process::exit(code);
    }
}

/// Runs one request and returns the exit code. The code is 0 for success and 2 for a request that the rule cannot
/// state. The code is 1 for a request that the inputs cannot satisfy.
fn run(arguments: impl IntoIterator<Item = OsString>) -> i32 {
    let (mode, options) = match read_request(arguments) {
        Ok(request) => request,
        Err(error) => return report(2, &error),
    };
    match mode {
        Some(Mode::EmbeddedProduct) => embedded_product::run(options),
        Some(Mode::ProductDescriptor) => product_descriptor::run(options),
        Some(Mode::ApplicationInfo) => application_info::run(options),
        Some(Mode::StampApplicationInfo) => stamp_application_info::run(options),
        None => run_plugin_descriptor(options),
    }
}

fn report(code: i32, error: &anyhow::Error) -> i32 {
    cli::report(&mut io::stderr(), error);
    code
}

/// Reads the request from the one argument that every rule passes, `--flagfile=<file>`, and takes its mode flag.
///
/// `use_param_file("--flagfile=%s", use_always = True)` of the rules writes a multiline file, one option per line. A
/// line is `--name=value` or a mode flag. The value never comes from the next line, and no rule writes an empty line.
fn read_request(arguments: impl IntoIterator<Item = OsString>) -> Result<(Option<Mode>, cli::Options)> {
    let mut arguments = cli::parse(arguments)?;
    let file = arguments.require("--flagfile")?;
    arguments.finish()?;
    let mut options = cli::parse(read_text(Path::new(&file))?.lines().map(OsString::from))?;
    let mode = take_mode(&mut options)?;
    Ok((mode, options))
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

/// Takes the mode flag of the request. A request without a mode flag stays on the plugin patching path.
fn take_mode(options: &mut cli::Options) -> Result<Option<Mode>> {
    let mut mode: Option<Mode> = None;
    for candidate in Mode::ALL {
        if !options.flag(candidate.flag())? {
            continue;
        }
        if let Some(selected) = mode {
            bail!("only one mode flag is allowed, got {} and {}", selected.flag(), candidate.flag());
        }
        mode = Some(candidate);
    }
    Ok(mode)
}

/// The request of one plugin, as the rule states it.
///
/// Every field is a string, a boolean, a path or a list of those. Nothing here can reach a layout or a build context.
/// `dev_dist_plugin_descriptor` states the request, and [`parse_plugin_request`] is its one reader.
#[derive(Debug, PartialEq, Eq)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "each field is one switch of the request that the rule states"
)]
struct PluginRequest {
    output: PathBuf,
    main_module: String,
    source: DescriptorSource,
    build_number_file: PathBuf,
    /// The application info of the product. The stamps take the EAP flag, the release date and the release version
    /// from it.
    application_info: PathBuf,
    /// The application info of the host product, for a frontend.
    host_application_info: Option<PathBuf>,
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
    plugin_descriptors: BTreeMap<String, PathBuf>,
    /// The descriptors that no production source root holds, keyed by load path and valued by the jars of the library
    /// container that answers it. The load path is also the zip entry, because `toLoadPath` strips the leading `/`.
    /// The rule declares a container and not a jar, so the jar that holds the entry is found here.
    plugin_descriptors_in_jar: BTreeMap<String, Vec<PathBuf>>,
    /// The raw text patch of the layout as marker-table rows, in the order it applies them.
    markers: Vec<String>,
    /// What the layout appends to the IDE build version. Empty for a layout that stamps the version unchanged.
    version_suffix: String,
    /// Replaces the since and until constraint that the flags give.
    compatible_build_range: Option<CompatibleBuildRange>,
    /// When set, receives the final descriptor after one more [`descriptorxml`] round trip. That is the form that the
    /// plugin classpath record embeds (`generatePluginClassPathFromOrderedAssets` of `orderedAssets.kt`).
    reserialized_output: Option<PathBuf>,
}

/// The source of the plugin descriptor: a declared file, or an entry of a declared jar.
#[derive(Debug, PartialEq, Eq)]
enum DescriptorSource {
    File(PathBuf),
    JarEntry { jar: PathBuf, entry: String },
}

fn run_plugin_descriptor(options: cli::Options) -> i32 {
    let parsed = match parse_plugin_request(options) {
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
    let mut outputs = vec![(parsed.output.as_path(), content.clone())];
    if let Some(reserialized_output) = &parsed.reserialized_output {
        match reserialize(&content) {
            Ok(reserialized) => outputs.push((reserialized_output.as_path(), reserialized)),
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

    let mut jars = Jars::default();
    let (source, source_name) = match &parsed.source {
        DescriptorSource::File(path) => (read_file(path)?, path),
        DescriptorSource::JarEntry { jar, entry } => (jars.read_first_entry(std::slice::from_ref(jar), entry)?, jar),
    };
    let source = String::from_utf8(source).with_context(|| format!("{} is not valid UTF-8", source_name.display()))?;
    let patched = markers::apply(&source, &parsed.markers)?;
    let cache = seed_cache(&parsed.plugin_descriptors, &parsed.plugin_descriptors_in_jar, &mut jars)?;

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

/// Reads the facts of the application info. A frontend reads its host too, as the product files tool does.
fn read_application_info(parsed: &PluginRequest) -> Result<ApplicationInfo> {
    ApplicationInfo::load(
        &parsed.application_info,
        &parsed.replacements,
        parsed.host_application_info.as_deref(),
        parsed.build_date_seconds,
    )
}

/// Reads the parameter file that `dev_dist_plugin_descriptor` writes, one option per line.
///
/// An option that the parser does not know fails the run. That is the rule of the platform too. It keeps the rule and
/// this binary on one spelling: a rule that grows an option reaches this parser or fails here.
fn parse_plugin_request(mut options: cli::Options) -> Result<PluginRequest> {
    let output = options.require("--out")?.into();
    let main_module = options.require("--main-module")?;
    let source = match (options.take("--source")?, options.take("--source-in-jar")?) {
        (Some(_), Some(_)) => bail!("the descriptor source is declared more than once"),
        (_, Some(value)) => {
            let (entry, jar) = parse_descriptor_jar(&value)?;
            DescriptorSource::JarEntry {
                jar: jar.into(),
                entry: entry.to_owned(),
            }
        }
        (Some(source), None) if !source.is_empty() => DescriptorSource::File(source.into()),
        (_, None) => bail!("--source is required"),
    };
    // The rule states the application info of every product, so a request without it is one that the rule cannot state.
    let build_number_file = options.require("--build-number-file")?.into();
    let application_info = options.require("--application-info-source")?.into();
    let host_application_info = non_empty_path(&mut options, "--host-application-info-source")?;
    let replacements = Replacement::parse_all(&options.take_all("--replacement")?)?;
    let build_date_seconds = options.require("--build-date-seconds")?;
    let Ok(build_date_seconds) = build_date_seconds.parse::<i64>() else {
        bail!("--build-date-seconds is not a number of seconds: '{build_date_seconds}'");
    };
    let mut plugin_descriptors = BTreeMap::new();
    for value in options.take_all("--plugin-descriptor")? {
        put_descriptor(&mut plugin_descriptors, &value)?;
    }
    let mut plugin_descriptors_in_jar = BTreeMap::new();
    for value in options.take_all("--plugin-descriptor-in-jar")? {
        append_descriptor_jar(&mut plugin_descriptors_in_jar, &value)?;
    }
    // The layouts state one range. `EXACT` and `RESTRICTED_TO_SAME_RELEASE` still come from the two flags.
    let compatible_build_range = match options.take("--compatible-build-range")?.as_deref() {
        None => None,
        Some("NEWER_WITH_SAME_BASELINE") => Some(CompatibleBuildRange::NewerWithSameBaseline),
        Some(other) => bail!("the compatible build range '{other}' is not supported, only NEWER_WITH_SAME_BASELINE is"),
    };
    let request = PluginRequest {
        output,
        main_module,
        source,
        build_number_file,
        application_info,
        host_application_info,
        replacements,
        build_date_seconds,
        exact_version: boolean_option(&mut options, "--exact-version", false)?,
        retain_product: boolean_option(&mut options, "--retain-product-descriptor", false)?,
        embeds_content: boolean_option(&mut options, "--embed-content-modules", true)?,
        reserialize_before_content_embedding: boolean_option(&mut options, "--reserialize-before-content-embedding", false)?,
        refused_content_modules: options.take_all("--refused-content-module")?,
        embedded_content_modules: options.take_all("--embed-content-module")?.into_iter().collect(),
        separate_jar: options.take_all("--separate-jar")?.into_iter().collect(),
        plugin_descriptors,
        plugin_descriptors_in_jar,
        markers: options.take_all("--marker")?,
        version_suffix: options.take("--version-suffix")?.unwrap_or_default(),
        compatible_build_range,
        reserialized_output: non_empty_path(&mut options, "--reserialized-output")?,
    };
    options.finish()?;
    Ok(request)
}

/// Reads an optional path option. The rules never write an empty value, so this function refuses one.
fn non_empty_path(options: &mut cli::Options, name: &str) -> Result<Option<PathBuf>> {
    match options.take(name)? {
        Some(value) if value.is_empty() => bail!("{name} must not be empty"),
        value => Ok(value.map(PathBuf::from)),
    }
}

/// Reads an optional boolean option with Kotlin's `String.toBooleanStrict`, which accepts exactly `true` and `false`.
fn boolean_option(options: &mut cli::Options, name: &str, default: bool) -> Result<bool> {
    options.take(name)?.map_or(Ok(default), |value| parse_boolean_strict(&value))
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
fn put_descriptor(into: &mut BTreeMap<String, PathBuf>, value: &str) -> Result<()> {
    let Some((load_path, file)) = value.split_once('=').filter(|(load_path, _)| !load_path.is_empty()) else {
        bail!("a descriptor is '<load path>=<file>', and '{value}' is not");
    };
    if into.insert(load_path.to_owned(), file.into()).is_some() {
        bail!("the load path '{load_path}' is declared twice");
    }
    Ok(())
}

/// Records a jar candidate for a load path.
///
/// One option for each (load path, jar), because the rule declares a library container and states every jar of it.
/// The order is the order of the container, and the first jar that has the entry answers.
fn append_descriptor_jar(into: &mut BTreeMap<String, Vec<PathBuf>>, value: &str) -> Result<()> {
    match value.split_once('=') {
        Some((load_path, jar)) if !load_path.is_empty() => {
            into.entry(load_path.to_owned()).or_default().push(jar.into());
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
fn seed_cache(files: &BTreeMap<String, PathBuf>, jars: &BTreeMap<String, Vec<PathBuf>>, opened: &mut Jars) -> Result<Cache> {
    let mut cache = Cache::default();
    for (load_path, file) in files {
        cache.insert(load_path.clone(), read_file(file)?)?;
    }
    for (load_path, candidates) in jars {
        cache.insert(load_path.clone(), opened.read_first_entry(candidates, load_path)?)?;
    }
    Ok(cache)
}

/// The declared jars of one request. A container states many load paths over the same jars, so each jar opens once, at
/// its first lookup, and stays open for the request.
#[derive(Default)]
struct Jars {
    opened: HashMap<PathBuf, Jar>,
}

impl Jars {
    /// Returns the entry of the first jar that has it.
    fn read_first_entry(&mut self, jars: &[PathBuf], name: &str) -> Result<Vec<u8>> {
        for jar in jars {
            if let Some(data) = self.read_entry(jar, name)? {
                return Ok(data);
            }
        }
        let names: Vec<String> = jars.iter().map(|jar| jar.display().to_string()).collect();
        bail!("no declared jar has the entry '{name}': {}", names.join(", "))
    }

    /// Returns the entry of the jar, or `None` when the jar has no entry of that name. The first record of the name
    /// answers.
    fn read_entry(&mut self, path: &Path, name: &str) -> Result<Option<Vec<u8>>> {
        if !self.opened.contains_key(path) {
            self.opened.insert(path.to_path_buf(), open_jar(path)?);
        }
        let jar = &self.opened[path];
        let Some(entry) = jar.entries().find(|entry| entry.name == name) else {
            return Ok(None);
        };
        let data = jar
            .data(&entry)
            .with_context(|| format!("{}: cannot read the entry '{name}'", path.display()))?;
        Ok(Some(data.into_owned()))
    }
}

/// Opens a declared jar with the reader of the packer. An I/O error names the jar. Every other error is a jar that the
/// reader refuses.
fn open_jar(path: &Path) -> Result<Jar> {
    Jar::open(path).map_err(|error| {
        if error.downcast_ref::<io::Error>().is_some() {
            error
        } else {
            anyhow!("not a valid zip file: {error:#}")
        }
    })
}

/// Reads a declared input. The error names the file.
fn read_file(path: &Path) -> Result<Vec<u8>> {
    std::fs::read(path).with_context(|| format!("open {}", path.display()))
}

/// Reads a declared text input. A file that is not UTF-8 fails, because the platform reads every input as UTF-8.
fn read_text(path: &Path) -> Result<String> {
    String::from_utf8(read_file(path)?).with_context(|| format!("{} is not valid UTF-8", path.display()))
}

/// Writes one output, and creates its directory first.
///
/// The directory gets the mode of [`std::fs::create_dir_all`] under the umask, because Bazel creates the directory of a
/// declared output before the action runs, and no inventory records the mode of a directory of the writer.
fn write_output(path: &Path, content: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        std::fs::create_dir_all(parent).with_context(|| parent.display().to_string())?;
    }
    std::fs::write(path, content).with_context(|| format!("open {}", path.display()))
}
