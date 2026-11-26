// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::fs;
use std::path::{self, Path, PathBuf};

use anyhow::{Context as _, Result, anyhow, bail};

use crate::merge::{MergeSpec, Source};
use crate::nativelib;
use crate::natives::{NativeSpec, NativeTree};

/// A parsed flag file: the jar to pack, and the span file of the run.
#[derive(Clone, Debug, Default)]
pub struct FlagFile {
    /// The jar of the `output=` line.
    pub spec: MergeSpec,
    /// The `trace-file=` destination, the span file of the run. Nothing about packing reads it. It comes in the flag
    /// file because a packing action passes no other argument.
    pub trace_file: Option<PathBuf>,
}

/// Reads the argument grammar of the packer: one `output=` line, then the `module=`, `library=` and `file=` lines that
/// the jar is built from.
///
/// A jar can merge thousands of inputs, which do not fit a command line, so the recipe is a flag file. One action packs
/// one jar, so the parser refuses a second `output=` line. The order of the source lines is the precedence that the
/// merge uses for duplicates. A relative path is resolved against `base_dir`.
///
/// `trace-file=` is here and not on the command line, because a packing action passes one argument, `--flagfile=`.
///
/// `native-tree=`, `native-variant=` and `native-lib=` together put the jar in natives mode. See [`NativeSpec`]. They
/// are three lines because each is a different kind of value: an output path, a platform token and a library name.
/// `native-lib=` alone only reserves the native entries of the library and writes no tree. Any other subset is a recipe
/// that says one thing and packs another, so the parser refuses it.
///
/// The parser takes only the forms that the Starlark rules and the recipe replay write:
///
/// - `keep-manifest=`, `merge-entities=` and `reject-native-entries=` take only `true`. A producer omits a false flag.
///   `keep-manifest=true` keeps the manifest of a library source. A module output keeps its manifest without it.
/// - A path has no `.` and no `..` component, so the parser compares the paths as they are written. See
///   [`resolve_path`].
///
/// The flag file must be UTF-8, because no Bazel path needs other bytes.
pub fn parse_flag_file(path: &Path, base_dir: &Path) -> Result<FlagFile> {
    let content = fs::read(path).with_context(|| path.display().to_string())?;
    let content = String::from_utf8(content).map_err(|error| anyhow!("{}: the flag file is not valid UTF-8: {error}", path.display()))?;
    let resolve = |value: &str| resolve_path(value, base_dir);

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
            if current.is_some() {
                bail!("a second `output=` line {value:?}: a recipe packs one jar");
            }
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
                if value.is_empty() || trace_file.is_some() {
                    bail!("expected one nonempty `trace-file=`");
                }
                trace_file = Some(resolve(value)?);
            }
            "metadata-file" => {
                if value.is_empty() || spec.metadata_file.is_some() {
                    bail!("expected one nonempty `metadata-file=` per output");
                }
                spec.metadata_file = Some(resolve(value)?);
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
    let Some(mut spec) = current else {
        bail!("{}: the flag file has no `output=` line", path.display());
    };
    spec.native = natives.spec().with_context(|| spec.output.display().to_string())?;
    check_destinations(&spec, trace_file.as_deref())?;
    Ok(FlagFile { spec, trace_file })
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

/// Refuses a recipe whose outputs collide with each other or with an input. The jar, the metadata file, the native
/// tree and the trace file are four destinations, and no source may be one of them.
#[expect(
    clippy::unnecessary_debug_formatting,
    reason = "the Debug form quotes the path, and a refusal keeps its text"
)]
fn check_destinations(spec: &MergeSpec, trace: Option<&Path>) -> Result<()> {
    if spec.sources.is_empty() {
        bail!("no inputs for {:?}", spec.output);
    }
    let tree = spec
        .native
        .as_ref()
        .and_then(|native| native.tree.as_ref())
        .map(|tree| tree.dir.as_path());
    if spec.native.is_some() && spec.reject_native_entries {
        bail!(
            "{}: `reject-native-entries=true` cannot be combined with the natives mode",
            spec.output.display()
        );
    }
    let destinations = [
        ("a jar output", Some(spec.output.as_path())),
        ("a metadata destination", spec.metadata_file.as_deref()),
        ("a native tree destination", tree),
        ("a trace destination", trace),
    ];
    for (index, (kind, path)) in destinations.iter().enumerate() {
        let Some(path) = path else { continue };
        if destinations[..index].iter().any(|(_, other)| other == &Some(*path)) {
            bail!("{kind} is another output of the recipe: {}", path.display());
        }
        if spec.sources.iter().any(|source| source.path() == *path) {
            bail!("{kind} is an input: {}", path.display());
        }
    }
    Ok(())
}

/// The three natives-mode lines of the recipe.
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

    /// The [`NativeSpec`] of the recipe, or `None` for a recipe with none of the three lines.
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
