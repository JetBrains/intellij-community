//! The runfiles of the launcher. One lookup resolves the runfiles of the launch manifest and of the local home.

use std::path::{Path, PathBuf};

use anyhow::bail;
use component::local_home::{RunfilesEnv, RunfilesLookup};

use crate::path_string;

pub(crate) struct Runfiles {
    env: RunfilesEnv,
    lookup: RunfilesLookup,
}

impl Runfiles {
    /// Finds the runfiles of the launcher `self_path`: `RUNFILES_DIR`, then `<self>.runfiles`, then
    /// `RUNFILES_MANIFEST_FILE` or `<self>.runfiles_manifest`. `bazel run` sets none of the variables.
    pub(crate) fn find(self_path: &str, getenv: &dyn Fn(&str) -> String) -> anyhow::Result<Self> {
        let mut env = None;
        for directory in [getenv("RUNFILES_DIR"), format!("{self_path}.runfiles")] {
            if !directory.is_empty() && Path::new(&directory).is_dir() {
                env = Some(RunfilesEnv {
                    runfiles_dir: Some(PathBuf::from(directory)),
                    ..RunfilesEnv::default()
                });
                break;
            }
        }
        if env.is_none() {
            for file in [getenv("RUNFILES_MANIFEST_FILE"), format!("{self_path}.runfiles_manifest")] {
                if !file.is_empty() && Path::new(&file).exists() {
                    env = Some(RunfilesEnv {
                        runfiles_manifest_file: Some(PathBuf::from(file)),
                        ..RunfilesEnv::default()
                    });
                    break;
                }
            }
        }
        let Some(env) = env else {
            bail!("no runfiles for {self_path}");
        };
        let lookup = RunfilesLookup::new(&env)?;
        Ok(Self { env, lookup })
    }

    /// The lookup that also links the runfiles of the local home.
    pub(crate) const fn lookup(&self) -> &RunfilesLookup {
        &self.lookup
    }

    /// The file of the runfile `name`, a path in slash form. A local Java runtime states its executable as an absolute
    /// path, which the lookup returns unchanged.
    pub(crate) fn rlocation(&self, name: &str) -> anyhow::Result<String> {
        if Path::new(name).is_absolute() {
            return Ok(name.to_owned());
        }
        path_string(self.lookup.resolve(name)?)
    }

    /// The variables that a child needs to find the same runfiles: the before-run step and the IDE, which the java
    /// stub gave `JAVA_RUNFILES`.
    pub(crate) fn environment(&self) -> Vec<(&'static str, String)> {
        let text = |path: &PathBuf| path.display().to_string();
        match (&self.env.runfiles_dir, &self.env.runfiles_manifest_file) {
            (Some(directory), _) => vec![("RUNFILES_DIR", text(directory)), ("JAVA_RUNFILES", text(directory))],
            (None, Some(file)) => vec![("RUNFILES_MANIFEST_FILE", text(file))],
            (None, None) => Vec::new(),
        }
    }
}
