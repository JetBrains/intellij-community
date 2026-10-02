// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::path::{self, Path, PathBuf};

use anyhow::{Context as _, Result, anyhow, bail};

use crate::merge::{MergeSpec, Source};
use crate::nativelib;
use crate::natives::{NativeSpec, NativeTree};

/// A parsed flag file: the groups in file order, and the span file of the run.
#[derive(Clone, Debug, Default)]
pub struct FlagFile {
    /// One [`MergeSpec`] per `output=` line.
    pub groups: Vec<MergeSpec>,
    /// The `trace-file=` destination, the span file of the *run*. Nothing about packing reads it. It comes in the flag
    /// file because a packing action passes no other argument. Every `trace-file=` line of the file names this path.
    pub trace_file: Option<PathBuf>,
}

/// Reads the argument grammar of the packer: one `output=` line per jar, then the `module=`, `library=` and `file=`
/// lines it is built from.
///
/// A product packs thousands of jars from thousands of inputs, which do not fit a command line, so the recipe is a
/// flag file. `output=` starts a group, so the file is ordered, and that order is the precedence the merge uses for
/// duplicates. A relative path is resolved against `base_dir`.
///
/// `trace-file=` is here and not on the command line, because a packing action passes one argument, `--flagfile=`. It
/// is a line inside a group. A run writes one trace, so the parser refuses two lines with different paths.
///
/// `native-tree=`, `native-variant=` and `native-lib=` together put a group in natives mode. See [`NativeSpec`]. They
/// are three lines because each is a different kind of value: an output path, a platform token and a library name.
/// `native-lib=` alone only reserves the native entries of the library and writes no tree. Any other subset is a recipe
/// that says one thing and packs another, so the parser refuses it.
///
/// The parser takes only the forms that the Starlark rules and the recipe replay write:
///
/// - `keep-manifest=`, `merge-entities=` and `reject-native-entries=` take only `true`. A producer omits a false flag.
/// - `jar-name=` takes one nonempty file name without `/` and `\`, at most once per group. It states the distribution
///   name of the jar, which can differ from the output file name. A group without it takes the output file name.
/// - A path has no `.` and no `..` component, so the parser compares the paths as they are written. See
///   [`resolve_path`].
///
/// The flag file must be UTF-8. The Go parser kept the raw bytes of a path, and no Bazel path needs that.
#[expect(
    clippy::unnecessary_debug_formatting,
    reason = "the Debug form quotes the path, and a refusal keeps its text"
)]
pub fn parse_flag_file(path: &Path, base_dir: &Path) -> Result<FlagFile> {
    let content = fs::read(path).with_context(|| path.display().to_string())?;
    let content = String::from_utf8(content).map_err(|error| anyhow!("{}: the flag file is not valid UTF-8: {error}", path.display()))?;
    let resolve = |value: &str| resolve_path(value, base_dir);

    let mut specs: Vec<MergeSpec> = Vec::new();
    let mut trace_file: Option<PathBuf> = None;
    let mut current: Option<MergeSpec> = None;
    let mut natives = NativeLines::default();

    for line in content.split('\n') {
        let line = line.trim_end_matches('\r');
        if line.trim().is_empty() {
            continue;
        }
        let Some((option, value)) = line.split_once('=') else {
            bail!("expected `option=value`, got {line:?}");
        };
        if option == "output" {
            flush(&mut specs, current.take(), &mut natives)?;
            current = Some(MergeSpec {
                output: resolve(value)?,
                ..MergeSpec::default()
            });
            continue;
        }
        let Some(spec) = current.as_mut() else {
            bail!("`{line}` before any `output=`");
        };
        match option {
            "keep-manifest" => spec.keep_manifest = parse_true(option, value)?,
            "merge-entities" => spec.merge_entities = parse_true(option, value)?,
            "trace-file" => {
                // A run writes one trace, so two groups with different destinations have no answer. The flag file of
                // an action holds one group. A flag file made by hand from many command lines can hold two. If every
                // span went into the first destination, bazel-out would get a file that no action produced.
                let destination = resolve(value)?;
                if let Some(trace) = &trace_file
                    && *trace != destination
                {
                    bail!(
                        "two `trace-file=` destinations, {trace:?} and {destination:?}: a run writes one trace, so pass \
                         `--trace-file=` for the whole run or drop the lines"
                    );
                }
                trace_file = Some(destination);
            }
            "metadata-file" => {
                if value.is_empty() || spec.metadata_file.is_some() {
                    bail!("expected one nonempty `metadata-file=` per output");
                }
                spec.metadata_file = Some(resolve(value)?);
            }
            "jar-name" => {
                if spec.jar_name.is_some() {
                    bail!("expected one `jar-name=` per output, got a second one in {line:?}");
                }
                if value.is_empty() || value.contains(['/', '\\']) {
                    bail!("expected a file name without a path separator in `jar-name=`, got {line:?}");
                }
                spec.jar_name = Some(value.to_string());
            }
            "reject-native-entries" => spec.reject_native_entries = parse_true(option, value)?,
            "native-tree" | "native-variant" | "native-lib" => {
                if value.is_empty() {
                    bail!("expected a nonempty `{option}=`");
                }
                natives.set(option, value, base_dir)?;
            }
            "module" => spec.sources.push(Source::module(resolve(value)?)),
            "library" => spec.sources.push(Source::library(resolve(value)?)),
            "file" | "patch" => {
                // `file=<entry name>=<path>`, cut at the first `=`. So the entry name has no `=` and the path can have
                // one. A jar entry name has none, and a `bazel-out` path can have one.
                let Some((name, file_path)) = value.split_once('=') else {
                    bail!("expected `file=<entry name>=<path>`, got {line:?}");
                };
                if name.is_empty() || file_path.is_empty() {
                    bail!("`file=` states an empty entry name or path in {line:?}");
                }
                let file_path = resolve(file_path)?;
                spec.sources.push(if option == "patch" {
                    Source::patch(name, file_path)
                } else {
                    Source::file(name, file_path)
                });
            }
            _ => bail!("unknown option {option:?} in {line:?}"),
        }
    }
    flush(&mut specs, current.take(), &mut natives)?;
    check_destinations(&specs, trace_file.as_deref())?;
    Ok(FlagFile { groups: specs, trace_file })
}

/// Resolves a path of a recipe against `base_dir`, and keeps an absolute path as it is. It refuses a path with a `.` or
/// a `..` component. Such a path has two spellings, and the recipe checks compare the paths as they are written.
pub fn resolve_path(value: &str, base_dir: &Path) -> Result<PathBuf> {
    if value
        .split(['/', path::MAIN_SEPARATOR])
        .any(|component| component == "." || component == "..")
    {
        bail!("the path {value:?} has a `.` or `..` component");
    }
    Ok(base_dir.join(value))
}

fn flush(specs: &mut Vec<MergeSpec>, current: Option<MergeSpec>, natives: &mut NativeLines) -> Result<()> {
    let Some(mut spec) = current else {
        return Ok(());
    };
    spec.native = std::mem::take(natives).spec().with_context(|| spec.output.display().to_string())?;
    specs.push(spec);
    Ok(())
}

#[expect(
    clippy::unnecessary_debug_formatting,
    reason = "the Debug form quotes the path, and a refusal keeps its text"
)]
fn check_destinations(specs: &[MergeSpec], trace: Option<&Path>) -> Result<()> {
    let mut seen: HashMap<&Path, usize> = HashMap::with_capacity(specs.len());
    for (i, spec) in specs.iter().enumerate() {
        if spec.sources.is_empty() {
            bail!("no inputs for {:?}", spec.output);
        }
        if let Some(previous) = seen.get(spec.output.as_path()) {
            bail!("{:?} is declared twice, at group {previous} and {i}", spec.output);
        }
        seen.insert(&spec.output, i);
    }
    let mut metadata_paths: HashSet<&Path> = HashSet::new();
    for metadata_file in specs.iter().filter_map(|spec| spec.metadata_file.as_deref()) {
        if seen.contains_key(metadata_file) || metadata_paths.contains(metadata_file) || trace == Some(metadata_file) {
            bail!("conflicting metadata destination: {}", metadata_file.display());
        }
        metadata_paths.insert(metadata_file);
    }
    let mut native_trees: HashSet<&Path> = HashSet::new();
    for spec in specs {
        let Some(native) = &spec.native else {
            continue;
        };
        if spec.reject_native_entries {
            bail!(
                "{}: `reject-native-entries=true` cannot be combined with a native tree",
                spec.output.display()
            );
        }
        let Some(tree) = native.tree.as_ref().map(|tree| tree.dir.as_path()) else {
            continue;
        };
        if seen.contains_key(tree) || metadata_paths.contains(tree) || native_trees.contains(tree) || trace == Some(tree) {
            bail!("conflicting native tree destination: {}", tree.display());
        }
        native_trees.insert(tree);
    }
    for path in specs.iter().flat_map(|spec| &spec.sources).map(Source::path) {
        if metadata_paths.contains(path) {
            bail!("metadata destination is an input: {}", path.display());
        }
        if native_trees.contains(path) {
            bail!("native tree destination is an input: {}", path.display());
        }
    }
    Ok(())
}

/// The three natives-mode lines of one group, until `output=` or the end of the file closes the group.
#[derive(Default)]
struct NativeLines {
    tree: Option<PathBuf>,
    variant: Option<String>,
    lib: Option<String>,
}

impl NativeLines {
    fn set(&mut self, option: &str, value: &str, base_dir: &Path) -> Result<()> {
        let occupied = match option {
            "native-tree" => self.tree.replace(resolve_path(value, base_dir)?).is_some(),
            "native-variant" => self.variant.replace(value.to_string()).is_some(),
            _ => self.lib.replace(value.to_string()).is_some(),
        };
        if occupied {
            bail!("expected one `{option}=` per output");
        }
        Ok(())
    }

    /// The [`NativeSpec`] of the group, or `None` for a group with none of the three lines.
    fn spec(self) -> Result<Option<NativeSpec>> {
        match (self.tree, self.variant, self.lib) {
            (None, None, None) => Ok(None),
            (None, None, Some(lib)) => Ok(Some(NativeSpec { lib_name: lib, tree: None })),
            (Some(dir), Some(variant), Some(lib)) => {
                let (family, arch) = nativelib::parse_variant(&variant)?;
                Ok(Some(NativeSpec {
                    lib_name: lib,
                    tree: Some(NativeTree { dir, family, arch }),
                }))
            }
            _ => bail!("`native-tree=` and `native-variant=` require each other and `native-lib=`"),
        }
    }
}

/// Reads a flag that a producer writes only as `true`. So a typo is an error, and not a silently false flag that
/// changes the bytes.
fn parse_true(option: &str, value: &str) -> Result<bool> {
    if value != "true" {
        bail!("expected `{option}=true`, got `{option}={value}`: a recipe omits a false flag");
    }
    Ok(true)
}

#[cfg(test)]
mod tests;
