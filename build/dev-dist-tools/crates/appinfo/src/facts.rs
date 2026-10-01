// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The reader of the application-info facts that `bin/product-info.json` states.
//!
//! It ports the constructor of `ApplicationInfoPropertiesImpl` (`ApplicationInfoPropertiesImpl.kt:63-138`) for a dev
//! distribution. A line reference below names a line of that file. The port supports no system-property override,
//! because a dev distribution sets none.

use std::path::Path;

use anyhow::{Context, Result, bail};

use crate::descriptorxml::{self, Element, Node};
use crate::document::{ApplicationInfoElements, Replacement, replace_markers};

/// The facts of one application info.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ApplicationInfo {
    /// `fullProductName` (line 124): the full name, or the product name.
    pub full_product_name: String,
    /// `edition` (line 125). An empty edition is `None`.
    pub edition: Option<String>,
    /// `fullVersion` (line 58): the `full` pattern over the four version parts.
    pub version: String,
    /// `majorVersion` (line 72).
    pub major_version: String,
    /// `minorVersionMainPart` (line 83): the minor version up to its first dot.
    pub minor_version_main_part: String,
    /// `versionSuffix` (lines 80-82): the suffix, or `EAP` for an EAP product that states none.
    pub version_suffix: Option<String>,
    /// `isEAP` (lines 77-79).
    pub is_eap: bool,
    /// `svgRelativePath` (lines 131-132): the SVG icon of the product, if it states one.
    pub svg_icon: Option<String>,
    /// `shortCompanyName` (line 130).
    pub short_company_name: String,
    /// `majorReleaseDate` (lines 101-123), as `yyyyMMdd`.
    pub major_release_date: String,
}

/// The values that `JetBrainsClientPropertiesForLaunchers.applicationInfoOverride` takes from the host product.
///
/// `editionName` is always `null` there, so this struct has no edition.
struct HostOverride {
    full_product_name: String,
    eap: Option<String>,
    major: Option<String>,
    minor: Option<String>,
    micro: Option<String>,
    patch: Option<String>,
    full: Option<String>,
    suffix: Option<String>,
    major_release_date: Option<String>,
}

impl HostOverride {
    fn parse(content: &str, file: &Path) -> Result<Self> {
        let host = ApplicationInfoElements::parse(content, file)?;
        let names = host.names();
        let Some(full_product_name) = names.attribute("fullname").or_else(|| names.attribute("product")) else {
            bail!("the product application info has no product name: {}", file.display());
        };
        let version = host.version();
        let value = |name: &str| version.attribute(name).map(str::to_owned);
        Ok(Self {
            full_product_name: full_product_name.to_owned(),
            eap: value("eap"),
            major: value("major"),
            minor: value("minor"),
            micro: value("micro"),
            patch: value("patch"),
            full: value("full"),
            suffix: value("suffix"),
            major_release_date: host.build().attribute("majorReleaseDate").map(str::to_owned),
        })
    }
}

impl ApplicationInfo {
    /// Reads the facts of the application info `path` of a product: the markers first, then the XML.
    ///
    /// A frontend passes the application info of its host product as `host`. A value of the host takes precedence, and
    /// the host text gets no marker replacement. `pinned_build_date_seconds` is the build date that the dev distribution
    /// pins. An EAP product without a `majorReleaseDate` takes the date of it.
    pub fn load(path: &Path, replacements: &[Replacement], host: Option<&Path>, pinned_build_date_seconds: i64) -> Result<Self> {
        let content = replace_markers(&read_text(path)?, replacements);
        match host {
            Some(host) => Self::read_frontend(&content, path, &read_text(host)?, host, pinned_build_date_seconds),
            None => Self::read(&content, path, pinned_build_date_seconds),
        }
    }

    /// Reads the facts of a product. `content` is the application info after the marker replacement, and `file` names
    /// it in an error.
    pub(crate) fn read(content: &str, file: &Path, pinned_build_date_seconds: i64) -> Result<Self> {
        read_facts(content, file, None, pinned_build_date_seconds)
    }

    /// Reads the facts of a frontend, whose names, version and release date come from its host product.
    ///
    /// It is `ApplicationInfoPropertiesImpl` with the override of `JetBrainsClientPropertiesForLaunchers`
    /// (`platform/buildScripts/src/JetBrainsClientPropertiesForLaunchers.kt:118-165`). A value of the host takes
    /// precedence, and a value that the host does not state falls back to the frontend. The host text is the raw file,
    /// because the override reads the file without a marker replacement.
    pub(crate) fn read_frontend(
        content: &str,
        file: &Path,
        host_content: &str,
        host_file: &Path,
        pinned_build_date_seconds: i64,
    ) -> Result<Self> {
        let host = HostOverride::parse(host_content, host_file)?;
        read_facts(content, file, Some(&host), pinned_build_date_seconds)
    }

    /// `releaseVersionForLicensing` (lines 54-55): the major version, the main part of the minor version, then `00`.
    pub fn release_version_for_licensing(&self) -> String {
        format!("{}{}00", self.major_version, self.minor_version_main_part)
    }

    /// `productNameWithEdition` (line 61): the full name, then a space and the edition when there is one.
    pub fn product_name_with_edition(&self) -> String {
        match &self.edition {
            Some(edition) => format!("{} {edition}", self.full_product_name),
            None => self.full_product_name.clone(),
        }
    }
}

/// Reads a declared application info. The error names the file.
fn read_text(path: &Path) -> Result<String> {
    std::fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))
}

fn read_facts(content: &str, file: &Path, host: Option<&HostOverride>, pinned_build_date_seconds: i64) -> Result<ApplicationInfo> {
    let root = descriptorxml::read(content).with_context(|| file.display().to_string())?;
    let child = |name: &str| first_child(&root, name);
    let required_child =
        |name: &str| child(name).with_context(|| format!("the application info has no {name} element: {}", file.display()));

    let version = required_child("version")?;
    let version_value = |name: &str, host_value: Option<&Option<String>>| -> Result<Option<String>> {
        match host_value.and_then(Option::as_ref) {
            Some(value) => Ok(Some(value.clone())),
            None => Ok(attribute(version, name, file)?.map(str::to_owned)),
        }
    };
    // Lines 72-76.
    let Some(major) = version_value("major", host.map(|host| &host.major))? else {
        bail!("the version element has no major attribute: {}", file.display());
    };
    let minor = version_value("minor", host.map(|host| &host.minor))?.unwrap_or_else(|| "0".to_owned());
    let micro = version_value("micro", host.map(|host| &host.micro))?.unwrap_or_else(|| "0".to_owned());
    let patch = version_value("patch", host.map(|host| &host.patch))?.unwrap_or_else(|| "0".to_owned());
    let full = version_value("full", host.map(|host| &host.full))?.unwrap_or_else(|| "{0}.{1}".to_owned());
    let version_text = format_version(&full, [&major, &minor, &micro, &patch]).with_context(|| file.display().to_string())?;
    // Line 83.
    let minor_version_main_part = minor.split('.').next().unwrap_or_default().to_owned();
    // Lines 77-79: Kotlin's `String?.toBoolean()` is a case-insensitive comparison with `true`.
    let is_eap = version_value("eap", host.map(|host| &host.eap))?.is_some_and(|eap| eap.eq_ignore_ascii_case("true"));
    // Lines 80-82.
    let version_suffix = version_value("suffix", host.map(|host| &host.suffix))?.or_else(|| is_eap.then(|| "EAP".to_owned()));

    // Lines 84-85 and 124-125. The product name is required even when a full name is stated.
    let names = required_child("names")?;
    let Some(product) = attribute(names, "product", file)? else {
        bail!("the names element has no product attribute: {}", file.display());
    };
    let full_product_name = match host {
        Some(host) => host.full_product_name.clone(),
        None => attribute(names, "fullname", file)?.unwrap_or(product).to_owned(),
    };
    let edition = attribute(names, "edition", file)?
        .filter(|edition| !edition.is_empty())
        .map(str::to_owned);

    // Lines 101-123.
    let build = required_child("build")?;
    let raw_release_date = match host.and_then(|host| host.major_release_date.clone()) {
        Some(value) => Some(value),
        None => attribute(build, "majorReleaseDate", file)?.map(str::to_owned),
    }
    .filter(|value| !value.is_empty());
    if !is_eap && raw_release_date.as_ref().is_none_or(|value| value.starts_with("__")) {
        bail!("majorReleaseDate may be omitted only for EAP: {}", file.display());
    }
    let major_release_date =
        format_major_release_date(raw_release_date.as_deref(), pinned_build_date_seconds).with_context(|| file.display().to_string())?;

    // Lines 128-130.
    let company = required_child("company")?;
    let Some(company_name) = attribute(company, "name", file)? else {
        bail!("the company element has no name attribute: {}", file.display());
    };
    let short_company_name = match attribute(company, "shortName", file)? {
        Some(short_name) => short_name.to_owned(),
        None => shorten_company_name(company_name).to_owned(),
    };

    // Lines 131-132. The EAP icon is not checked for an empty value.
    let svg_path = match child("icon") {
        Some(icon) => attribute(icon, "svg", file)?.filter(|svg| !svg.is_empty()),
        None => None,
    };
    let eap_svg_path = match child("icon-eap") {
        Some(icon) if is_eap => attribute(icon, "svg", file)?,
        _ => None,
    };
    let svg_icon = eap_svg_path.or(svg_path).map(str::to_owned);

    Ok(ApplicationInfo {
        full_product_name,
        edition,
        version: version_text,
        major_version: major,
        minor_version_main_part,
        version_suffix,
        is_eap,
        svg_icon,
        short_company_name,
        major_release_date,
    })
}

/// `XmlElement.getChild` of `readXmlAsModel`: the first child element with this local name, in any namespace.
fn first_child<'a>(root: &'a Element, name: &str) -> Option<&'a Element> {
    root.children
        .iter()
        .filter_map(Node::as_element)
        .find(|element| element.name == name)
}

/// `XmlElement.getAttributeValue` of `readXmlAsModel`, which keys an attribute by its local name.
///
/// The Kotlin map keeps the last of two attributes with one local name, such as `edition` and `other:edition`. No
/// application info states such a pair, so the reader refuses it.
fn attribute<'a>(element: &'a Element, name: &str, file: &Path) -> Result<Option<&'a str>> {
    let mut matches = element
        .attributes
        .iter()
        .filter(|attribute| attribute.name.rsplit(':').next() == Some(name));
    let found = matches.next();
    if matches.next().is_some() {
        bail!(
            "the {} element has two attributes with the local name {name}: {}",
            element.name,
            file.display()
        );
    }
    Ok(found.map(|attribute| attribute.value.as_str()))
}

/// `MessageFormat.format(pattern, major, minor, micro, patch)` (line 58) for the subset that the application infos use.
///
/// The subset is literal text and the elements `{0}` to `{3}`. A quote or any other format element changes the
/// meaning in `MessageFormat`, so the function refuses it.
pub fn format_version(pattern: &str, parts: [&str; 4]) -> Result<String> {
    let mut result = String::new();
    let mut rest = pattern;
    while let Some(index) = rest.find(['{', '}', '\'']) {
        result.push_str(&rest[..index]);
        let tail = &rest[index..];
        let Some(part) = tail
            .strip_prefix('{')
            .and_then(|tail| tail.get(..2))
            .and_then(|element| element.strip_suffix('}'))
            .and_then(|digit| digit.parse::<usize>().ok())
            .and_then(|index| parts.get(index))
        else {
            bail!("the version pattern {pattern:?} is not literal text with the elements {{0}} to {{3}}");
        };
        result.push_str(part);
        rest = &tail[3..];
    }
    result.push_str(rest);
    Ok(result)
}

/// `formatMajorReleaseDate` (lines 304-319).
///
/// No value, or a `__` marker, gives the build date as `yyyyMMdd` in UTC. A `yyyyMMdd` value stays. A `yyyyMMddHHmm`
/// value loses the time. The function refuses every other value, which the Kotlin parser also refuses.
pub fn format_major_release_date(raw: Option<&str>, build_date_seconds: i64) -> Result<String> {
    let Some(raw) = raw.filter(|raw| !raw.starts_with("__")) else {
        let (year, month, day) = civil_date(build_date_seconds.div_euclid(86_400));
        return Ok(format!("{year:04}{month:02}{day:02}"));
    };
    let digits: Option<Vec<u32>> = raw.chars().map(|char| char.to_digit(10)).collect();
    let number = |digits: &[u32]| digits.iter().fold(0, |value, digit| value * 10 + digit);
    let valid = digits.as_deref().is_some_and(|digits| {
        let date_valid = |date: &[u32]| {
            let (year, month, day) = (number(&date[..4]), number(&date[4..6]), number(&date[6..8]));
            (1..=12).contains(&month) && (1..=days_in_month(year, month)).contains(&day)
        };
        match digits.len() {
            8 => date_valid(digits),
            12 => date_valid(&digits[..8]) && number(&digits[8..10]) < 24 && number(&digits[10..12]) < 60,
            _ => false,
        }
    });
    if !valid {
        bail!("the major release date {raw:?} is neither yyyyMMdd nor yyyyMMddHHmm");
    }
    Ok(raw[..8].to_owned())
}

const fn days_in_month(year: u32, month: u32) -> u32 {
    match month {
        2 if year.is_multiple_of(4) && (!year.is_multiple_of(100) || year.is_multiple_of(400)) => 29,
        2 => 28,
        4 | 6 | 9 | 11 => 30,
        _ => 31,
    }
}

/// The proleptic Gregorian date of a day count since 1970-01-01. It is `civil_from_days` of Howard Hinnant.
fn civil_date(days: i64) -> (i64, i64, i64) {
    let days = days + 719_468;
    let era = days.div_euclid(146_097);
    let day_of_era = days.rem_euclid(146_097);
    let year_of_era = (day_of_era - day_of_era / 1460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let month_index = (5 * day_of_year + 2) / 153;
    let day = day_of_year - (153 * month_index + 2) / 5 + 1;
    let month = if month_index < 10 { month_index + 3 } else { month_index - 9 };
    let year = year_of_era + era * 400 + i64::from(month <= 2);
    (year, month, day)
}

/// `linuxFrameClass` of `BuildTasksImpl.kt`, which `AppUIUtil.getFrameClass` repeats.
pub fn linux_frame_class(product_name_with_edition: &str) -> String {
    let name = product_name_with_edition
        .to_lowercase()
        .replace(' ', "-")
        .replace("intellij-idea", "idea")
        .replace("android-studio", "studio")
        .replace("-community-edition", "-ce")
        .replace("-ultimate-edition", "")
        .replace("-professional-edition", "");
    if name.starts_with("jetbrains-") {
        name
    } else {
        format!("jetbrains-{name}")
    }
}

/// `shortenCompanyName` (line 291): the name without the suffix ` s.r.o.`, then without the suffix ` Inc.`.
pub fn shorten_company_name(name: &str) -> &str {
    let name = name.strip_suffix(" s.r.o.").unwrap_or(name);
    name.strip_suffix(" Inc.").unwrap_or(name)
}
