//! The fakes of the composer tests: the manifests, the source bindings, the staged trees and the copy steps. Only the
//! tests of the command line change the working directory. They hold a [`testkit::WorkingDirectory`], and every other
//! test uses absolute paths in a [`testkit::TempDir`].

use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};

use component::manifest::{ComponentEntry, ComponentManifest, MANIFEST_VERSION};
use component::paths;
use testkit::{TempDir, file_symlink, write_file};

use crate::compose::{self, ComposeOptions, ComposedBuild, DevBuildComponent};
use crate::spec::{self, ComponentSources, CompositionComponent};

pub(crate) fn test_manifest(kind: &str) -> ComponentManifest {
    ComponentManifest {
        version: MANIFEST_VERSION,
        kind: kind.to_owned(),
        platform_prefix: "idea".to_owned(),
        os: "linux".to_owned(),
        arch: "x64".to_owned(),
        main_class: Some("com.intellij.idea.Main".to_owned()),
        ..ComponentManifest::default()
    }
}

pub(crate) fn with_entries(mut manifest: ComponentManifest, entries: Vec<ComponentEntry>) -> ComponentManifest {
    manifest.entries = entries;
    manifest
}

/// A file entry with the hash 1 and a source below `inputs/`, for a composition that copies nothing.
pub(crate) fn file_entry(relative_path: &str) -> ComponentEntry {
    sourced_entry(relative_path, &format!("inputs/{relative_path}"))
}

pub(crate) fn sourced_entry(relative_path: &str, source: &str) -> ComponentEntry {
    file_with_mode(relative_path, source, false, None)
}

pub(crate) fn file_with_mode(relative_path: &str, source: &str, executable: bool, mode: Option<u32>) -> ComponentEntry {
    ComponentEntry::ComponentFile {
        relative_path: relative_path.to_owned(),
        hash: 1,
        executable,
        source: source.to_owned(),
        mode,
    }
}

pub(crate) fn link_entry(relative_path: &str, target: &str) -> ComponentEntry {
    ComponentEntry::Symlink {
        relative_path: relative_path.to_owned(),
        hash: filemeta::hash_symlink_target(target),
        symlink_target: target.to_owned(),
    }
}

pub(crate) fn directory_entry(relative_path: &str, mode: u32) -> ComponentEntry {
    ComponentEntry::Directory {
        relative_path: relative_path.to_owned(),
        mode,
    }
}

/// A component whose sources are bound as file artifacts, as the Starlark rule binds a full distribution.
///
/// Bazel stages only the declared inputs, so the bindings hold only the sources that exist and are valid host paths.
pub(crate) fn bound(directory: &TempDir, manifest: ComponentManifest) -> DevBuildComponent {
    let sources: Vec<&str> = manifest
        .entries
        .iter()
        .filter_map(|entry| match entry {
            ComponentEntry::ComponentFile { source, .. } => Some(source.as_str()),
            _ => None,
        })
        .filter(|source| paths::host_path(source).is_ok() && Path::new(source).exists())
        .collect();
    let bindings = bind_files(directory, &manifest.kind, &sources);
    DevBuildComponent {
        source_bindings: Some(bindings),
        ..DevBuildComponent::new(manifest)
    }
}

/// A component with empty source bindings, for a component with links and directories only.
pub(crate) fn unbound_files(manifest: ComponentManifest) -> DevBuildComponent {
    DevBuildComponent {
        source_bindings: Some(ComponentSources::default()),
        ..DevBuildComponent::new(manifest)
    }
}

/// Binds each source as a file artifact. The bindings file is in `directory`, and each source must be below it.
pub(crate) fn bind_files(directory: &TempDir, component: &str, sources: &[&str]) -> ComponentSources {
    let file = directory.path().join(format!("{component}.source-bindings.jsonl"));
    let mut lines = String::new();
    let mut known = HashSet::new();
    for source in sources.iter().filter(|source| known.insert(**source)) {
        let relative = Path::new(source)
            .strip_prefix(directory.path())
            .expect("a source in the test directory");
        let relative = paths::to_slash(relative.to_str().expect("a UTF-8 path")).into_owned();
        lines.push_str(&binding_line(component, source, &relative, "file", &[]));
        lines.push('\n');
    }
    write_file(&file, lines);
    read_bindings(&file, component)
}

/// One line of the source bindings file, as `_add_source_bindings` in `intellij_dev_dist.bzl` writes it.
pub(crate) fn binding_line(component: &str, source: &str, anchor_relative: &str, kind: &str, members: &[&str]) -> String {
    format!(
        r#"{{"component":{},"source":{},"anchorRelativePath":{},"type":{},"members":{}}}"#,
        json(component),
        json(source),
        json(anchor_relative),
        json(kind),
        serde_json::to_string(members).expect("JSON")
    )
}

pub(crate) fn read_bindings(file: &Path, component: &str) -> ComponentSources {
    let components = [CompositionComponent {
        manifest: component.to_owned(),
        plugin_classpath_part: None,
    }];
    let mut bindings = spec::read_source_bindings(file.to_str().expect("a UTF-8 path"), &components).expect("the source bindings");
    bindings.remove(component).expect("the bindings of the component")
}

pub(crate) fn json(value: &str) -> String {
    serde_json::to_string(value).expect("JSON")
}

/// A tree artifact that Bazel stages in a sandbox: each staged member links to the physical output, and the bindings
/// file describes the tree.
#[derive(Debug)]
pub(crate) struct BoundTree {
    pub(crate) physical: PathBuf,
    pub(crate) staged: PathBuf,
    pub(crate) bindings: ComponentSources,
}

/// A staged tree before the composer reads its bindings, so that a test can change the tree first.
pub(crate) struct StagedTree {
    pub(crate) physical: PathBuf,
    pub(crate) staged: PathBuf,
    directory: PathBuf,
}

impl StagedTree {
    /// Writes the bindings file of the tree with `members` and reads it as the composer does.
    pub(crate) fn bind(self, members: &[&str]) -> anyhow::Result<BoundTree> {
        let physical_metadata = self.directory.join("physical/metadata/bindings.jsonl");
        let staged_metadata = self.directory.join("sandbox/metadata/bindings.jsonl");
        let line = binding_line(
            "plugin",
            self.staged.to_str().expect("a UTF-8 path"),
            "../trees/plugin",
            "directory",
            members,
        );
        write_file(&physical_metadata, line);
        fs::create_dir_all(staged_metadata.parent().expect("a parent")).expect("the directory");
        file_symlink(&physical_metadata, &staged_metadata);
        let components = [CompositionComponent {
            manifest: "plugin".to_owned(),
            plugin_classpath_part: None,
        }];
        let mut bindings = spec::read_source_bindings(staged_metadata.to_str().expect("a UTF-8 path"), &components)?;
        Ok(BoundTree {
            physical: self.physical,
            staged: self.staged,
            bindings: bindings.remove("plugin").expect("the bindings of the component"),
        })
    }
}

impl BoundTree {
    pub(crate) fn new(directory: &Path, members: &[&str]) -> Self {
        Self::stage(directory).bind(members).expect("the source bindings")
    }

    /// Creates the physical tree with `lib/native.jar` and the staged tree that links to it.
    pub(crate) fn stage(directory: &Path) -> StagedTree {
        let physical = directory.join("physical/trees/plugin");
        let staged = directory.join("sandbox/trees/plugin");
        for tree in [&physical, &staged] {
            fs::create_dir_all(tree.join("lib")).expect("the tree");
        }
        write_file(physical.join("lib/native.jar"), "native bytes");
        file_symlink(physical.join("lib/native.jar"), staged.join("lib/native.jar"));
        StagedTree {
            physical,
            staged,
            directory: directory.to_path_buf(),
        }
    }

    pub(crate) fn staged(&self, member: &str) -> String {
        self.staged.join(member).to_str().expect("a UTF-8 path").to_owned()
    }
}

/// Composes a full distribution with the merge step of the composer.
pub(crate) fn compose(components: &[DevBuildComponent], target: impl AsRef<Path>) -> anyhow::Result<ComposedBuild> {
    compose_with(components, target, ComposeOptions::default())
}

/// Composes under a tracer that records, as `main` does with `--trace-file`, so each test also runs the span calls.
#[expect(clippy::needless_pass_by_value, reason = "the tests build the options inline")]
pub(crate) fn compose_with(
    components: &[DevBuildComponent],
    target: impl AsRef<Path>,
    options: ComposeOptions,
) -> anyhow::Result<ComposedBuild> {
    let root = trace::Tracer::new("dev-dist-composer test").span(crate::JOB_NAME);
    compose::compose_components(components, target.as_ref(), &options, &root)
}

pub(crate) fn with_directory_runfiles(directory: impl AsRef<Path>, runfile: &str) -> ComposeOptions {
    let runfiles = BTreeMap::from([(directory.as_ref().to_path_buf(), runfile.to_owned())]);
    ComposeOptions {
        source_directory_runfiles: Some(runfiles),
        ..ComposeOptions::default()
    }
}

#[track_caller]
pub(crate) fn require_link(path: impl AsRef<Path>, expected: &str) {
    let path = path.as_ref();
    let target = fs::read_link(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    let target = target.to_str().expect("a UTF-8 target");
    assert_eq!(paths::to_slash(target), expected, "the link {}", path.display());
}

/// Checks the permission bits of a file. The check does nothing on Windows, because the composer sets no bits there.
#[track_caller]
pub(crate) fn require_mode(path: impl AsRef<Path>, expected: u32) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        let path = path.as_ref();
        let mode = fs::metadata(path).expect("the metadata").permissions().mode() & 0o7777;
        assert_eq!(mode, expected, "the mode of {} is {mode:o}", path.display());
    }
    #[cfg(not(unix))]
    let _ = (path, expected);
}

/// A copy step that must not run, for launch metadata and for the checks before the first write.
pub(crate) fn no_merge(_: &[DevBuildComponent], _: &Path) -> anyhow::Result<()> {
    panic!("the composition merged component files")
}

/// A copy step that copies nothing, for a test of the metadata of a full distribution without payload.
#[expect(clippy::unnecessary_wraps, reason = "the signature of a copy step")]
pub(crate) fn skip_merge(_: &[DevBuildComponent], _: &Path) -> anyhow::Result<()> {
    Ok(())
}

/// Runfiles keyed by absolute paths, as `compose::absolute_keys` gives them.
pub(crate) fn runfiles(pairs: &[(&str, &str)]) -> BTreeMap<PathBuf, String> {
    pairs.iter().map(|(key, value)| (PathBuf::from(key), (*value).to_owned())).collect()
}
