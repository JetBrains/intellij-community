//! The home spec: `<target>.home.json`, which `intellij_dev_java_launcher` writes for each launch target.
//!
//! The spec names the source file of each destination of the home by its absolute path, so the tool reads no runfiles
//! tree and no runfiles manifest. The local layout of the distribution adds the hash, the mode, the directories and the
//! symbolic links. The spec also holds the launch of the target, which the tool passes through to the `json` answer.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use serde::Deserialize;

/// The only spec version.
pub(crate) const SPEC_VERSION: u32 = 1;

/// The spec of one launch target. Every path is absolute.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct HomeSpec {
    pub(crate) version: u32,
    /// The label of the launch target. The garbage collection keeps the newest generations of each target.
    pub(crate) target: String,
    /// The default root of the store and the generations, next to the output base.
    pub(crate) root: PathBuf,
    /// The fingerprint file of the local launch. Its text names the generation.
    pub(crate) fingerprint: PathBuf,
    /// The directory below the generation that holds the home: `Contents` for a macOS app, or empty.
    #[serde(default)]
    pub(crate) home_prefix: String,
    /// `local-layout.json` of the local launch.
    pub(crate) layout: PathBuf,
    /// The source file of a destination of the home. A destination that the layout does not list is a metadata file,
    /// such as `fingerprint.txt`, and the tool hashes it.
    pub(crate) files: BTreeMap<String, PathBuf>,
    /// The source directory of a tree destination. A layout file below the destination is at the same relative path in
    /// the directory.
    #[serde(default)]
    pub(crate) trees: BTreeMap<String, PathBuf>,
    /// The native launcher, relative to the home.
    pub(crate) launcher: String,
    /// The vmoptions file of the target, with the flags of the row.
    pub(crate) vm_options: PathBuf,
    /// The variable `<ENV>_VM_OPTIONS` that names the vmoptions file for the launcher.
    pub(crate) vm_options_variable: String,
    /// Whether the vmoptions file of the generation names `modules/module-descriptors.dat` of the home.
    #[serde(default)]
    pub(crate) runtime_module_repository: bool,
    /// The launch of the target, for an IDE run handler. The generation stores none of it, so two checkouts with other
    /// launches share one generation.
    pub(crate) launch: LaunchSpec,
}

/// The launch of a target as `bazel run` starts its stub, without the launcher and the vmoptions file.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct LaunchSpec {
    /// The program arguments of the row, before the arguments of the run configuration.
    pub(crate) program_arguments: Vec<String>,
    /// The directory where `bazel run` starts the stub.
    pub(crate) working_directory: PathBuf,
    /// The variables that `bazel run` sets: the ones of `RunEnvironmentInfo`, `BUILD_WORKING_DIRECTORY` and
    /// `BUILD_WORKSPACE_DIRECTORY`.
    pub(crate) env: BTreeMap<String, String>,
    /// The variables that `bazel run` removes from the environment of the stub.
    pub(crate) unset_env: Vec<String>,
}

/// Reads and checks the spec at `path`.
pub(crate) fn read_spec(path: &Path) -> Result<HomeSpec> {
    let text = fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))?;
    let spec: HomeSpec = serde_json::from_str(&text).with_context(|| format!("{} is not a home spec", path.display()))?;
    validate(&spec).with_context(|| format!("the home spec {} is not valid", path.display()))?;
    Ok(spec)
}

fn validate(spec: &HomeSpec) -> Result<()> {
    if spec.version != SPEC_VERSION {
        bail!("the version is {}, not {SPEC_VERSION}. Build the target again", spec.version);
    }
    if spec.target.is_empty() {
        bail!("`target` is empty");
    }
    if !spec.home_prefix.is_empty() {
        distpath::validate_path(&spec.home_prefix).context("`homePrefix`")?;
    }
    distpath::validate_path(&spec.launcher).context("`launcher`")?;
    if spec.vm_options_variable.is_empty()
        || !spec
            .vm_options_variable
            .bytes()
            .all(|byte| byte.is_ascii_uppercase() || byte.is_ascii_digit() || byte == b'_')
    {
        bail!("`vmOptionsVariable` is not a variable name: {:?}", spec.vm_options_variable);
    }
    for (name, path) in [
        ("root", &spec.root),
        ("fingerprint", &spec.fingerprint),
        ("layout", &spec.layout),
        ("vmOptions", &spec.vm_options),
        ("launch.workingDirectory", &spec.launch.working_directory),
    ] {
        if !path.is_absolute() {
            bail!("`{name}` is not an absolute path: {}", path.display());
        }
    }
    for (destination, source) in spec.files.iter().chain(&spec.trees) {
        distpath::validate_path(destination).with_context(|| format!("the destination {destination:?}"))?;
        if !source.is_absolute() {
            bail!("the source of {destination} is not an absolute path: {}", source.display());
        }
    }
    for name in spec.launch.env.keys().chain(&spec.launch.unset_env) {
        if name.is_empty() || name.contains(['=', '\0']) {
            bail!("`launch` names a variable that is not a variable name: {name:?}");
        }
    }
    Ok(())
}

impl HomeSpec {
    /// The source file of the layout file `destination`: a file of `files`, or a file inside the deepest tree.
    pub(crate) fn source(&self, destination: &str) -> Result<PathBuf> {
        if let Some(file) = self.files.get(destination) {
            return Ok(file.clone());
        }
        let mut end = destination.len();
        while let Some(slash) = destination[..end].rfind('/') {
            if let Some(tree) = self.trees.get(&destination[..slash]) {
                return Ok(tree.join(&destination[slash + 1..]));
            }
            end = slash;
        }
        bail!("the home spec names no source for {destination}")
    }
}

#[cfg(test)]
mod tests;
