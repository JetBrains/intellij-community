//! The fixtures that the tests of the crate share.

use std::path::{Path, PathBuf};

use crate::{Recipe, parse_recipe};

/// A recipe with the head comment, in the shape that the emitter writes. One output has two `zip` sources, one has an
/// `inMemory` source, and one has a `lazy` source for a set of jars.
pub(crate) const SAMPLE_PLAN: &str = "\
# The packaging recipe the 'plugins_sample' dev-distribution fragment executed, in the checked-in content-report schema (com.intellij.platform.distributionContent.FileEntry).
# Written by DevDistRecipe; 3 outputs.
- name: plugins/sample/lib/modules/intellij.sample.pure.jar
  contentModules:
  - name: intellij.sample.pure
  kind: jar
  sources:
  - kind: zip
    label: '@@lib+//:some-library'
    file: some-library-1.2.jar
    filter: keyed
    filterCacheKey:
    - a/**
  - kind: zip
    label: '@@community+//plugins/sample:pure.jar'
    module: intellij.sample.pure
    filter: keyed
    filterCacheKey:
    - a/**
- name: plugins/sample/lib/sample.jar
  modules:
  - name: intellij.sample
  kind: jar
  sources:
  - kind: inMemory
    name: META-INF/plugin.xml
    size: 4096
    needsCode: true
  - kind: zip
    label: '@@community+//plugins/sample:sample.jar'
    module: intellij.sample
    filter: unkeyed
- name: plugins/sample/lib/drivers.jar
  contentModules:
  - name: intellij.sample.drivers
  kind: jar
  sources:
  - kind: lazy
    name: a-1.jar,b-2.jar
    hash: -12345
    needsCode: true
  - kind: zip
    label: '@@//plugins/sample/drivers:drivers.jar'
    module: intellij.sample.drivers
    filter: unkeyed
";

pub(crate) fn sample_recipe() -> Recipe {
    parse_recipe(Path::new("plugins_sample.plan.yaml"), SAMPLE_PLAN).unwrap_or_else(|error| panic!("{error}"))
}

/// A plan text with the head comment of `DevDistRecipe` for `count` outputs.
pub(crate) fn plan(count: usize, body: &str) -> String {
    format!(
        "# The packaging recipe the 'sample' dev-distribution fragment executed, in the checked-in content-report \
         schema (com.intellij.platform.distributionContent.FileEntry).\n# Written by DevDistRecipe; {count} outputs.\n\
         {body}"
    )
}

/// Lays out a distribution with one file of `size` zero bytes for each path.
pub(crate) fn write_dist(files: &[(&str, usize)]) -> tempfile::TempDir {
    let root = tempfile::tempdir().unwrap();
    for (path, size) in files {
        let full = root.path().join(path);
        std::fs::create_dir_all(full.parent().unwrap()).unwrap();
        std::fs::write(full, vec![0u8; *size]).unwrap();
    }
    root
}

/// `testdata/` of the crate. Bazel names it in `DDT_TESTDATA_DIR`, and `cargo test` sets the run-time
/// `CARGO_MANIFEST_DIR`.
pub(crate) fn testdata_dir() -> PathBuf {
    std::env::var_os("DDT_TESTDATA_DIR")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("CARGO_MANIFEST_DIR").map(|directory| PathBuf::from(directory).join("testdata")))
        .expect("DDT_TESTDATA_DIR or CARGO_MANIFEST_DIR names the test data")
}
