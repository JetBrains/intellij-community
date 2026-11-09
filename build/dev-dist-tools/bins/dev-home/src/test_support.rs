//! A launch target in a temporary directory: Bazel outputs, a local layout, a home spec and a root.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use component::layout::{LOCAL_LAYOUT_VERSION, LocalFileKind, LocalLayout, LocalLayoutFile};
use testkit::{TempDir, write_file};

use crate::generation::{self, Materialized};
use crate::spec::{HomeSpec, LaunchSpec, SPEC_VERSION};
use crate::store::Store;

/// The files of the home and their first content. `bin/idea` is executable, and `jbr` is a tree.
const FILES: [(&str, &str); 6] = [
    ("bin/idea", "launcher"),
    ("lib/app.jar", "app"),
    ("lib/util.jar", "util"),
    ("lib/same-1.jar", "same"),
    ("lib/same-2.jar", "same"),
    ("modules/module-descriptors.dat", "modules"),
];
const TREE_FILES: [(&str, &str); 2] = [("jbr/bin/java", "java"), ("jbr/release", "release")];

pub(crate) struct Fixture {
    pub(crate) directory: TempDir,
    pub(crate) spec: HomeSpec,
}

impl Fixture {
    /// A target with the home prefix `prefix` and the fingerprint `v5:first`.
    pub(crate) fn new(prefix: &str) -> Self {
        let directory = TempDir::new();
        let out = directory.path().join("bazel-out");
        for (path, content) in FILES {
            write_file(out.join(path), content);
        }
        for (path, content) in TREE_FILES {
            write_file(out.join("jbr-tree").join(path.strip_prefix("jbr/").unwrap()), content);
        }
        write_file(out.join("idea.fingerprint"), "v5:first");
        write_file(out.join("idea.vmoptions"), "-Xmx2g\n");
        let mut files: BTreeMap<String, PathBuf> = FILES.iter().map(|(path, _)| ((*path).to_owned(), out.join(path))).collect();
        files.insert("fingerprint.txt".to_owned(), out.join("idea.fingerprint"));
        let spec = HomeSpec {
            version: SPEC_VERSION,
            target: "//build:idea".to_owned(),
            root: directory.path().join("root"),
            fingerprint: out.join("idea.fingerprint"),
            home_prefix: prefix.to_owned(),
            layout: out.join("local-layout.json"),
            files,
            trees: BTreeMap::from([("jbr".to_owned(), out.join("jbr-tree"))]),
            launcher: "bin/idea".to_owned(),
            vm_options: out.join("idea.vmoptions"),
            vm_options_variable: "IDEA_VM_OPTIONS".to_owned(),
            runtime_module_repository: true,
            launch: launch(directory.path()),
        };
        let fixture = Self { directory, spec };
        fixture.write_layout();
        fixture
    }

    pub(crate) fn root(&self) -> &Path {
        &self.spec.root
    }

    pub(crate) fn store(&self) -> Store {
        Store::new(self.root()).unwrap()
    }

    pub(crate) fn materialize(&self) -> anyhow::Result<Materialized> {
        generation::materialize(&self.spec, self.root(), &self.store())
    }

    /// The Bazel output of the home file `path`.
    pub(crate) fn source(&self, path: &str) -> PathBuf {
        self.spec.source(path).unwrap()
    }

    /// Replaces a Bazel output as Bazel does: deletes the file and writes a new one. The layout keeps the old hash.
    pub(crate) fn rebuild_source(&self, path: &str, content: &str) {
        let source = self.source(path);
        fs::remove_file(&source).unwrap();
        write_file(&source, content);
    }

    /// Writes the fingerprint file, the next launch then builds another generation.
    pub(crate) fn set_fingerprint(&self, fingerprint: &str) {
        let file = &self.spec.fingerprint;
        fs::remove_file(file).unwrap();
        write_file(file, fingerprint);
    }

    /// Writes `local-layout.json` with the current hashes of the Bazel outputs.
    pub(crate) fn write_layout(&self) {
        let mut entries = vec![
            directory("bin", None),
            directory("lib", None),
            directory("modules", None),
            directory("jbr", None),
            directory("jbr/bin", Some(0o755)),
        ];
        for (path, _) in FILES.iter().chain(&TREE_FILES) {
            let hash = xxh3::hash_file(&self.source(path)).unwrap();
            entries.push(LocalLayoutFile {
                path: (*path).to_owned(),
                runfile: Some(format!("_main/{path}")),
                executable: matches!(*path, "bin/idea" | "jbr/bin/java"),
                hash: Some(hash),
                ..LocalLayoutFile::default()
            });
        }
        entries.push(LocalLayoutFile {
            path: "lib/link.jar".to_owned(),
            symlink_target: Some("app.jar".to_owned()),
            ..LocalLayoutFile::default()
        });
        let layout = LocalLayout {
            version: LOCAL_LAYOUT_VERSION,
            files: entries,
            metadata: vec!["fingerprint.txt".to_owned()],
        };
        let file = &self.spec.layout;
        let _ = fs::remove_file(file);
        write_file(file, serde_json::to_vec(&layout).unwrap());
    }
}

/// The launch of the fixture target: one program argument, the working directory and the variables of `bazel run`.
pub(crate) fn launch(directory: &Path) -> LaunchSpec {
    LaunchSpec {
        program_arguments: vec!["--nosplash".to_owned()],
        working_directory: directory.join("idea.runfiles/_main"),
        env: BTreeMap::from([
            ("BUILD_WORKSPACE_DIRECTORY".to_owned(), "/work".to_owned()),
            ("JAVA_RUNFILES".to_owned(), directory.join("idea.runfiles").display().to_string()),
        ]),
        unset_env: vec!["RUNFILES_MANIFEST_FILE".to_owned()],
    }
}

impl Fixture {
    /// Rewrites `local-layout.json` through `edit`.
    pub(crate) fn edit_layout(&self, edit: impl FnOnce(&mut LocalLayout)) {
        let file = &self.spec.layout;
        let mut layout: LocalLayout = serde_json::from_slice(&fs::read(file).unwrap()).unwrap();
        edit(&mut layout);
        fs::remove_file(file).unwrap();
        write_file(file, serde_json::to_vec(&layout).unwrap());
    }
}

fn directory(path: &str, mode: Option<u32>) -> LocalLayoutFile {
    LocalLayoutFile {
        path: path.to_owned(),
        mode,
        kind: Some(LocalFileKind::Directory),
        ..LocalLayoutFile::default()
    }
}

/// Sets the modification time of `path` to `age` ago.
pub(crate) fn age(path: &Path, age: Duration) {
    let file = fs::File::options().write(true).open(path).unwrap();
    file.set_modified(SystemTime::now() - age).unwrap();
}

/// The names in a directory, sorted.
pub(crate) fn names(directory: &Path) -> Vec<String> {
    let mut names: Vec<String> = fs::read_dir(directory)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    names
}
