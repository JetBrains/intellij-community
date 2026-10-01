//! The purity weighing: how much of what a distribution build packs is already expressible as data.
//!
//! The unit is the output and its bytes, not the source. One source whose content is in no file makes the whole jar
//! unbuildable from data. A share over sources describes the sources and not the distribution.

use std::collections::{BTreeMap, HashSet};
use std::fmt;
use std::ops::{AddAssign, Sub};

use crate::{Distribution, FileEntry, Recipe, RecipeSource};

/// The maximum number of example paths in [`Purity::pure_outputs`] and [`Purity::unmeasured`].
const EXAMPLES: usize = 12;

/// Why one source of an output is not expressible as a label and a filter from a closed vocabulary.
///
/// [`RecipeSource::blocker`] reads it from the `kind` and the `filter` of the source, never from `needsCode`. The flag
/// is the claim of the emitter, and the kind is what the emitter observed. [`Purity::needs_code_disagreement`] counts
/// the sources where the two disagree.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Blocker {
    /// An `inMemory` source: bytes that are in no file, so no label can name them. Mostly a patched `plugin.xml`.
    GeneratedContent,
    /// A `lazy` source: one name for a set of files that the build resolves while it runs.
    LazyName,
    /// A `customAsset` source: arbitrary code makes the content.
    CustomAsset,
    /// A file-backed source that no label names. The recipe states a path, which depends on the machine.
    PathInsteadOfLabel,
    /// `filter: unkeyed`: the filter is the exclude constant `commonModuleExcludes` and nothing else.
    ConstantFilter,
    /// `filter: keyed`: the exclude constant and the globs of `filterCacheKey`. The packer flag file has no word for a
    /// per-source exclude set, so this is harder than [`Blocker::ConstantFilter`].
    KeyedFilter,
    /// A source kind that the classifier does not know. A new word must not read as pure.
    UnknownSourceKind,
    /// A filter word that the classifier does not know. A new word must not read as pure.
    UnknownFilter,
    /// An output with no source: `kind: link` and `kind: placed`. The recipe does not record the file, so this is a
    /// gap in the recipe and not a dependency on code.
    NoSourceRecorded,
}

impl Blocker {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::GeneratedContent => "generated-content",
            Self::LazyName => "lazy-name",
            Self::CustomAsset => "custom-asset",
            Self::PathInsteadOfLabel => "path-instead-of-label",
            Self::ConstantFilter => "constant-filter",
            Self::KeyedFilter => "keyed-filter",
            Self::UnknownSourceKind => "unknown-source-kind",
            Self::UnknownFilter => "unknown-filter-word",
            Self::NoSourceRecorded => "no-source-recorded",
        }
    }
}

impl fmt::Display for Blocker {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.as_str())
    }
}

/// The blocker of a source kind whose content no label can name.
fn code_kind_blocker(kind: &str) -> Option<Blocker> {
    match kind {
        "inMemory" => Some(Blocker::GeneratedContent),
        "lazy" => Some(Blocker::LazyName),
        "customAsset" => Some(Blocker::CustomAsset),
        _ => None,
    }
}

/// The source kinds whose content is a file that the build already has.
fn is_file_backed(kind: &str) -> bool {
    matches!(kind, "zip" | "dir" | "file" | "unpackedZip")
}

impl RecipeSource {
    /// Why this source is not expressible as a label and a filter from a closed vocabulary. `None` when it is.
    pub fn blocker(&self) -> Option<Blocker> {
        if let Some(blocker) = code_kind_blocker(&self.kind) {
            return Some(blocker);
        }
        if !is_file_backed(&self.kind) {
            return Some(Blocker::UnknownSourceKind);
        }
        if self.label.is_none() {
            return Some(Blocker::PathInsteadOfLabel);
        }
        match self.filter.as_deref() {
            None => None,
            Some("unkeyed") => Some(Blocker::ConstantFilter),
            Some("keyed") => Some(Blocker::KeyedFilter),
            Some(_) => Some(Blocker::UnknownFilter),
        }
    }
}

impl FileEntry {
    /// The module that the jar of this entry is named for. `None` for an entry with no member.
    ///
    /// The path decides first when a member confirms it: `lib/modules/<X>.jar` is named for X when X is a member. Then
    /// the first `modules` member decides, then the first `contentModules` member. Each fallback alone named a wrong
    /// module for a real entry. `modules` first names a passenger in `lib/modules/intellij.station.aia.jar`.
    /// `contentModules` first names a passenger in `lib/remdev-plugin.jar`.
    pub fn primary_member(&self) -> Option<&str> {
        if let Some(named) = self.path.strip_prefix("lib/modules/") {
            let named = named.strip_suffix(".jar").unwrap_or(named);
            if self.modules.iter().chain(&self.content_modules).any(|member| member == named) {
                return Some(named);
            }
        }
        self.modules.first().or(self.content_modules.first()).map(String::as_str)
    }
}

/// Outputs, jars and bytes of one group: one blocker, one cause set, or one owning module.
///
/// `jars + unjoined + duplicate == entries` holds in every row. An output that the distribution does not hold is
/// unjoined. An output with a path that another output already named is one file on disk, so it is a duplicate and
/// its bytes count once.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Weight {
    pub entries: usize,
    pub jars: usize,
    pub bytes: u64,
    pub unjoined: usize,
    pub duplicate: usize,
}

impl Weight {
    /// Whether the three outcomes account for every output in the row. A row that does not balance is a counting bug.
    pub const fn balances(&self) -> bool {
        self.jars + self.unjoined + self.duplicate == self.entries
    }
}

impl AddAssign for Weight {
    fn add_assign(&mut self, other: Self) {
        self.entries += other.entries;
        self.jars += other.jars;
        self.bytes += other.bytes;
        self.unjoined += other.unjoined;
        self.duplicate += other.duplicate;
    }
}

/// The weight of a group without one of its subgroups. The subtrahend must be a subgroup of `self`.
impl Sub for Weight {
    type Output = Self;

    fn sub(self, other: Self) -> Self {
        Self {
            entries: self.entries - other.entries,
            jars: self.jars - other.jars,
            bytes: self.bytes - other.bytes,
            unjoined: self.unjoined - other.unjoined,
            duplicate: self.duplicate - other.duplicate,
        }
    }
}

/// The answer for one output: which of its sources block a data-only executor.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct OutputPurity {
    /// [`FileEntry::primary_member`], or `(no module)`.
    pub owner: String,
    /// The distinct blockers, sorted by name.
    pub causes: Vec<Blocker>,
}

impl OutputPurity {
    /// The exact combination of blockers, so the groups that it keys partition the outputs.
    pub fn cause_set(&self) -> String {
        if self.causes.is_empty() {
            return "pure data".to_owned();
        }
        let names: Vec<&str> = self.causes.iter().map(|cause| cause.as_str()).collect();
        names.join(" + ")
    }

    /// Whether a cause other than the two filter words blocks the output. Such an output stays unbuildable from data
    /// when the exclude set becomes data.
    pub fn needs_code(&self) -> bool {
        self.causes
            .iter()
            .any(|cause| !matches!(cause, Blocker::ConstantFilter | Blocker::KeyedFilter))
    }
}

/// What a run over a set of recipes found.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Purity {
    pub recipes: usize,
    /// The fragment names, sorted.
    pub fragments: Vec<String>,
    pub outputs: usize,
    pub sources: usize,
    /// Every output that the recipes cover. A share against it answers how much of the distribution a data-only
    /// executor can make.
    pub total: Weight,
    /// The outputs with a source list: the packed jars. A share against it answers how much of the described packing
    /// is already data.
    pub sourced: Weight,
    /// The outputs with no blocker.
    pub strict: Weight,
    /// The outputs whose only blockers are the two filter words. A superset of `strict`.
    pub modulo_filter: Weight,
    /// The outputs by entry kind.
    pub by_entry_kind: BTreeMap<String, Weight>,
    /// The outputs by their exact combination of blockers. The rows sum to `total`.
    pub by_cause_set: BTreeMap<String, Weight>,
    /// The outputs by blocker. An output counts once for each blocker, so the rows overlap.
    pub by_cause: BTreeMap<String, Weight>,
    /// `by_cause` by owning module.
    pub by_cause_owner: BTreeMap<String, BTreeMap<String, Weight>>,
    /// The source kinds as they occur, also a word that the classifier does not know.
    pub by_source_kind: BTreeMap<String, usize>,
    /// The filter words as they occur.
    pub by_filter: BTreeMap<String, usize>,
    /// The sources whose `needsCode` flag disagrees with their kind.
    pub needs_code_disagreement: usize,
    /// The sources for `packNativePresignedFiles`.
    pub presigned_sources: usize,
    /// At most 12 paths of the outputs with no blocker.
    pub pure_outputs: Vec<String>,
    /// At most 12 paths of the outputs that the distribution does not hold.
    pub unmeasured: Vec<String>,
    /// `<fragment>: <path>` for each output whose path an earlier output already named.
    pub duplicate_paths: Vec<String>,
}

/// Classifies every output of every recipe and weighs it against `dist`.
///
/// A byte is the on-disk size of the file that the distribution build wrote. With no `dist`, every output is
/// unjoined: the output shares stay valid, and no byte column reads as zero.
pub fn weigh_purity(recipes: &[Recipe], dist: Option<&Distribution>) -> Purity {
    let mut purity = Purity {
        recipes: recipes.len(),
        ..Purity::default()
    };
    let mut seen = HashSet::new();
    for recipe in recipes {
        purity.fragments.push(recipe.fragment.clone());
        for entry in &recipe.entries {
            let mut output = OutputPurity {
                owner: entry.primary_member().unwrap_or("(no module)").to_owned(),
                causes: Vec::new(),
            };
            for source in &entry.sources {
                purity.sources += 1;
                *purity.by_source_kind.entry(source.kind.clone()).or_default() += 1;
                if let Some(filter) = &source.filter {
                    *purity.by_filter.entry(filter.clone()).or_default() += 1;
                }
                if source.presigned {
                    purity.presigned_sources += 1;
                }
                if code_kind_blocker(&source.kind).is_some() != source.needs_code {
                    purity.needs_code_disagreement += 1;
                }
                if let Some(blocker) = source.blocker()
                    && !output.causes.contains(&blocker)
                {
                    output.causes.push(blocker);
                }
            }
            if entry.sources.is_empty() {
                // A denominator without the linked and placed files would report a share of the packing as a share
                // of the distribution.
                output.causes.push(Blocker::NoSourceRecorded);
            }
            output.causes.sort_by_key(|cause| cause.as_str());

            let mut one = Weight {
                entries: 1,
                ..Weight::default()
            };
            if !seen.insert(entry.path.as_str()) {
                one.duplicate = 1;
                purity.duplicate_paths.push(format!("{}: {}", recipe.fragment, entry.path));
            } else if let Some(size) = dist.and_then(|dist| dist.lookup_from_root(&entry.path)) {
                one.jars = 1;
                one.bytes = size;
            } else {
                one.unjoined = 1;
                add_example(&mut purity.unmeasured, &entry.path);
            }

            purity.outputs += 1;
            purity.total += one;
            add_to(&mut purity.by_entry_kind, entry.kind.as_str(), one);
            if !entry.sources.is_empty() {
                purity.sourced += one;
            }
            if output.causes.is_empty() {
                purity.strict += one;
                add_example(&mut purity.pure_outputs, &entry.path);
            }
            if !output.needs_code() {
                purity.modulo_filter += one;
            }
            add_to(&mut purity.by_cause_set, &output.cause_set(), one);
            for cause in &output.causes {
                add_to(&mut purity.by_cause, cause.as_str(), one);
                let owners = purity.by_cause_owner.entry(cause.as_str().to_owned()).or_default();
                add_to(owners, &output.owner, one);
            }
        }
    }
    purity.fragments.sort();
    purity
}

fn add_to(groups: &mut BTreeMap<String, Weight>, key: &str, one: Weight) {
    match groups.get_mut(key) {
        Some(weight) => *weight += one,
        None => {
            groups.insert(key.to_owned(), one);
        }
    }
}

/// Adds `path` while the list has fewer than 12 examples. A small row stays printable, and the truncation shows a large
/// row.
fn add_example(examples: &mut Vec<String>, path: &str) {
    if examples.len() < EXAMPLES {
        examples.push(path.to_owned());
    }
}

#[cfg(test)]
mod tests;
