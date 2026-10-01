//! The typed reader of one `<fragment>.plan.yaml`.

use std::fmt::Display;
use std::fs;
use std::path::{Path, PathBuf};

use saphyr::{LoadableYamlNode, MarkedYaml, Scalar, YamlData};

use anyhow::{Result, anyhow, bail};

const HEAD_FRAGMENT: &str = "# The packaging recipe the '";
const HEAD_COUNT: &str = "# Written by DevDistRecipe; ";
const HEAD_COUNT_END: &str = " outputs.";

const ENTRY_KEYS: &str = "name, kind, modules, contentModules and sources";
const SOURCE_KEYS: &str = "kind, label, path, file, module, prefix, filter, filterCacheKey, presigned, name, size, hash and needsCode";

/// How the assembly produced one output: the `kind` of a `FileEntry`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum EntryKind {
    /// A jar that the packer wrote. Only this kind has sources.
    Jar,
    /// A symbolic link in the distribution.
    Link,
    /// A file that reached the distribution by another route than the packer.
    Placed,
}

impl EntryKind {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Jar => "jar",
            Self::Link => "link",
            Self::Placed => "placed",
        }
    }
}

/// One output of a recipe: one `FileEntry` in the shape that `DevDistRecipe` writes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FileEntry {
    /// The `name` key: the output path from the distribution root.
    pub path: String,
    pub kind: EntryKind,
    /// The member names of `modules`, without the `/<descriptor>` suffix.
    pub modules: Vec<String>,
    /// The member names of `contentModules`, without the `/<descriptor>` suffix.
    pub content_modules: Vec<String>,
    /// The ordered sources that the packer consumed. Empty for a `link` or a `placed` output.
    pub sources: Vec<RecipeSource>,
}

/// One ordered source of one output. Every field that `DevDistRecipe` can write is here, also a field that no plan
/// of the repository has yet.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RecipeSource {
    /// The `Source` subtype. The reader keeps a word that it does not know, and [`RecipeSource::blocker`] reports it.
    pub kind: String,
    /// The input-manifest label of a file-backed source.
    pub label: Option<String>,
    /// The macro-rooted path of a file-backed source that no label names.
    pub path: Option<String>,
    /// The file of `label` that the source read, when `label` declares more than one file.
    pub file: Option<String>,
    /// The module that owns the source, when the source is a module output.
    pub module: Option<String>,
    pub prefix: Option<String>,
    /// `keyed` or `unkeyed`. `None` when no filter ran. The reader keeps a word that it does not know.
    pub filter: Option<String>,
    pub filter_cache_key: Vec<String>,
    pub presigned: bool,
    /// The path of an `inMemory` source in the jar, or the name of a `lazy` source. An empty name reads as `None`,
    /// because the emitter writes the empty name of a nameless `lazy` source.
    pub name: Option<String>,
    pub size: i64,
    /// The precomputed hash of a `lazy` source. 0 when the source states none.
    pub hash: i64,
    /// The claim of the emitter that build code makes the bytes.
    pub needs_code: bool,
}

/// One executed recipe of one fragment.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Recipe {
    /// The plan file as the caller named it.
    pub file: PathBuf,
    /// The fragment name from the head comment. The schema has no field for it.
    pub fragment: String,
    pub entries: Vec<FileEntry>,
}

/// Interprets one plan file.
///
/// The two head comment lines of `DevDistRecipe` must be present. The first names the fragment, and the second states
/// the output count. The count must be equal to the number of entries, so a truncated plan cannot read as a smaller
/// distribution.
pub fn parse_recipe(file: &Path, source: &str) -> Result<Recipe> {
    let (fragment, declared) = parse_head(file, source)?;
    let reader = Reader { file };
    let documents = MarkedYaml::load_from_str(source).map_err(|error| anyhow!("{}: {error}", file.display()))?;
    let [document] = documents.as_slice() else {
        bail!(
            "{}: the plan holds {} YAML documents; DevDistRecipe writes one",
            file.display(),
            documents.len()
        );
    };
    let entries = reader
        .sequence("the plan", document)?
        .iter()
        .map(|entry| reader.entry(entry))
        .collect::<Result<Vec<_>, _>>()?;
    if declared != entries.len() {
        bail!("{}: the plan says {declared} outputs and holds {}", file.display(), entries.len());
    }
    Ok(Recipe {
        file: file.to_path_buf(),
        fragment,
        entries,
    })
}

/// Reads the plan files that `paths` name: a `.plan.yaml` file, or a directory that holds them.
///
/// A directory with no plan file is an error. A flag-off build prunes the plan files, so an empty directory is the
/// normal state and must not read as a distribution with no outputs.
pub fn read_recipes(paths: &[PathBuf]) -> Result<Vec<Recipe>> {
    let mut files = Vec::new();
    for path in paths {
        let metadata = fs::metadata(path).map_err(|error| io_error(path, &error))?;
        if !metadata.is_dir() {
            files.push(path.clone());
            continue;
        }
        let mut found = Vec::new();
        for entry in fs::read_dir(path).map_err(|error| io_error(path, &error))? {
            let name = entry.map_err(|error| io_error(path, &error))?.file_name();
            if name.to_string_lossy().ends_with(".plan.yaml") {
                found.push(path.join(name));
            }
        }
        if found.is_empty() {
            bail!(
                "{} holds no *.plan.yaml; a flag-off build prunes them, so re-run with \
                 --@community//platform/build-scripts/bazel-rules:dev_dist_plans --output_groups=+dev_dist_plans",
                path.display()
            );
        }
        files.extend(found);
    }
    // The byte order of the whole path decides the order of the plans, and not the component order of `Path`.
    files.sort_by(|a, b| a.as_os_str().as_encoded_bytes().cmp(b.as_os_str().as_encoded_bytes()));
    files
        .iter()
        .map(|file| {
            let source = fs::read_to_string(file).map_err(|error| io_error(file, &error))?;
            parse_recipe(file, &source)
        })
        .collect()
}

fn io_error(path: &Path, error: &std::io::Error) -> anyhow::Error {
    anyhow!("{}: {error}", path.display())
}

/// Reads the fragment name and the output count from the two head comment lines.
fn parse_head(file: &Path, source: &str) -> Result<(String, usize)> {
    let mut lines = source.split('\n');
    let fragment = lines
        .next()
        .and_then(|line| line.strip_prefix(HEAD_FRAGMENT))
        .and_then(|rest| rest.split_once('\''))
        .map(|(fragment, _)| fragment)
        .filter(|fragment| !fragment.is_empty())
        .ok_or_else(|| {
            anyhow!(
                "{}:1: the head comment names no fragment; DevDistRecipe writes `{HEAD_FRAGMENT}<fragment>' ...`",
                file.display()
            )
        })?;
    let declared = lines
        .next()
        .and_then(|line| line.strip_prefix(HEAD_COUNT))
        .and_then(|rest| rest.strip_suffix(HEAD_COUNT_END))
        .and_then(|count| count.parse::<usize>().ok())
        .ok_or_else(|| {
            anyhow!(
                "{}:2: the head comment states no output count; DevDistRecipe writes `{HEAD_COUNT}<n>{HEAD_COUNT_END}`",
                file.display()
            )
        })?;
    Ok((fragment.to_owned(), declared))
}

/// The accessors of the typed reading. Each refusal names the file, the line and the field.
struct Reader<'a> {
    file: &'a Path,
}

type Fields<'n, 'i> = Vec<(&'n str, &'n MarkedYaml<'i>, &'n MarkedYaml<'i>)>;

impl Reader<'_> {
    fn error(&self, node: &MarkedYaml<'_>, message: impl Display) -> anyhow::Error {
        anyhow!("{}:{}: {message}", self.file.display(), node.span.start.line())
    }

    /// The fields of a mapping in document order: the key text, the key node and the value node.
    fn mapping<'n, 'i>(&self, what: &str, node: &'n MarkedYaml<'i>) -> Result<Fields<'n, 'i>> {
        let YamlData::Mapping(mapping) = &node.data else {
            return Err(self.error(node, format!("{what} is {}, want a mapping", shape(node))));
        };
        mapping
            .iter()
            .map(|(key, value)| match &key.data {
                YamlData::Value(Scalar::String(text)) => Ok((text.as_ref(), key, value)),
                _ => Err(self.error(key, format!("{what} has a key that is {}, want a string", shape(key)))),
            })
            .collect()
    }

    fn sequence<'n, 'i>(&self, what: &str, node: &'n MarkedYaml<'i>) -> Result<&'n [MarkedYaml<'i>]> {
        match &node.data {
            YamlData::Sequence(items) => Ok(items),
            _ => Err(self.error(node, format!("{what} is {}, want a sequence", shape(node)))),
        }
    }

    fn string(&self, key: &str, node: &MarkedYaml<'_>) -> Result<String> {
        match &node.data {
            YamlData::Value(Scalar::String(text)) if text.is_empty() => {
                Err(self.error(node, format!("`{key}` is an empty string; DevDistRecipe writes no empty value")))
            }
            YamlData::Value(Scalar::String(text)) => Ok(text.to_string()),
            _ => Err(self.error(node, format!("`{key}` is {}, want a string", shape(node)))),
        }
    }

    /// A string that can be empty. The empty string reads as `None`.
    fn string_or_empty(&self, key: &str, node: &MarkedYaml<'_>) -> Result<Option<String>> {
        match &node.data {
            YamlData::Value(Scalar::String(text)) if text.is_empty() => Ok(None),
            _ => self.string(key, node).map(Some),
        }
    }

    fn integer(&self, key: &str, node: &MarkedYaml<'_>) -> Result<i64> {
        match &node.data {
            YamlData::Value(Scalar::Integer(value)) => Ok(*value),
            _ => Err(self.error(node, format!("`{key}` is {}, want an integer", shape(node)))),
        }
    }

    fn boolean(&self, key: &str, node: &MarkedYaml<'_>) -> Result<bool> {
        match &node.data {
            YamlData::Value(Scalar::Boolean(value)) => Ok(*value),
            _ => Err(self.error(node, format!("`{key}` is {}, want a boolean", shape(node)))),
        }
    }

    fn unknown_key(&self, what: &str, key: &str, node: &MarkedYaml<'_>, known: &str) -> anyhow::Error {
        self.error(
            node,
            format!("{what} holds `{key}`, which DevDistRecipe does not write; it writes {known}"),
        )
    }

    fn entry(&self, node: &MarkedYaml<'_>) -> Result<FileEntry> {
        let mut path = None;
        let mut kind = None;
        let mut modules = Vec::new();
        let mut content_modules = Vec::new();
        let mut sources = Vec::new();
        for (key, key_node, value) in self.mapping("an entry", node)? {
            match key {
                "name" => path = Some(self.string(key, value)?),
                "kind" => kind = Some(self.entry_kind(value)?),
                "modules" => modules = self.members(key, value)?,
                "contentModules" => content_modules = self.members(key, value)?,
                "sources" => {
                    sources = (self.sequence("`sources`", value)?.iter())
                        .map(|source| self.source(source))
                        .collect::<Result<_, _>>()?;
                }
                _ => return Err(self.unknown_key("an entry", key, key_node, ENTRY_KEYS)),
            }
        }
        let path = path.ok_or_else(|| self.error(node, "an entry states no `name`"))?;
        let kind = kind.ok_or_else(|| self.error(node, format!("the entry `{path}` states no `kind`")))?;
        Ok(FileEntry {
            path,
            kind,
            modules,
            content_modules,
            sources,
        })
    }

    fn entry_kind(&self, node: &MarkedYaml<'_>) -> Result<EntryKind> {
        match self.string("kind", node)?.as_str() {
            "jar" => Ok(EntryKind::Jar),
            "link" => Ok(EntryKind::Link),
            "placed" => Ok(EntryKind::Placed),
            other => Err(self.error(
                node,
                format!("the entry kind `{other}` is unknown; DevDistRecipe writes jar, link or placed"),
            )),
        }
    }

    /// The names of a `modules` or `contentModules` list. `DevDistRecipe` writes a member with a name only.
    fn members(&self, key: &str, node: &MarkedYaml<'_>) -> Result<Vec<String>> {
        let what = format!("a member of `{key}`");
        (self.sequence(&format!("`{key}`"), node)?.iter())
            .map(|member| {
                let mut name = None;
                for (field, field_node, value) in self.mapping(&what, member)? {
                    match field {
                        "name" => name = Some(self.string(field, value)?),
                        _ => return Err(self.unknown_key(&what, field, field_node, "name")),
                    }
                }
                let name = name.ok_or_else(|| self.error(member, format!("{what} states no `name`")))?;
                // `moduleName/descriptorName` names the module `moduleName`.
                Ok(match name.rfind('/') {
                    Some(slash) => name[..slash].to_owned(),
                    None => name,
                })
            })
            .collect()
    }

    fn source(&self, node: &MarkedYaml<'_>) -> Result<RecipeSource> {
        let mut kind = None;
        let mut source = RecipeSource::default();
        for (key, key_node, value) in self.mapping("a source", node)? {
            match key {
                "kind" => kind = Some(self.string(key, value)?),
                "label" => source.label = Some(self.string(key, value)?),
                "path" => source.path = Some(self.string(key, value)?),
                "file" => source.file = Some(self.string(key, value)?),
                "module" => source.module = Some(self.string(key, value)?),
                "prefix" => source.prefix = Some(self.string(key, value)?),
                "filter" => source.filter = Some(self.string(key, value)?),
                "filterCacheKey" => {
                    source.filter_cache_key = (self.sequence("`filterCacheKey`", value)?.iter())
                        .map(|glob| self.string(key, glob))
                        .collect::<Result<_, _>>()?;
                }
                "presigned" => source.presigned = self.boolean(key, value)?,
                "name" => source.name = self.string_or_empty(key, value)?,
                "size" => source.size = self.integer(key, value)?,
                "hash" => source.hash = self.integer(key, value)?,
                "needsCode" => source.needs_code = self.boolean(key, value)?,
                _ => return Err(self.unknown_key("a source", key, key_node, SOURCE_KEYS)),
            }
        }
        source.kind = kind.ok_or_else(|| self.error(node, "a source states no `kind`"))?;
        Ok(source)
    }
}

/// The name of the shape of a node, for a refusal.
const fn shape(node: &MarkedYaml<'_>) -> &'static str {
    match &node.data {
        YamlData::Value(Scalar::Null) => "null",
        YamlData::Value(Scalar::Boolean(_)) => "a boolean",
        YamlData::Value(Scalar::Integer(_)) => "an integer",
        YamlData::Value(Scalar::FloatingPoint(_)) => "a floating-point number",
        YamlData::Value(Scalar::String(_)) => "a string",
        YamlData::Representation(..) => "an unresolved scalar",
        YamlData::Sequence(_) => "a sequence",
        YamlData::Mapping(_) => "a mapping",
        YamlData::Tagged(..) => "a tagged node",
        YamlData::Alias(_) => "an alias",
        YamlData::BadValue => "an unreadable value",
    }
}

#[cfg(test)]
mod tests;
