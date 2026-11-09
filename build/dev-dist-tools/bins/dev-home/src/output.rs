//! The three forms of the answer of `materialize`.
//!
//! - `json`, for an IDE run handler: `{"home", "launcher", "vmOptionsFile", "vmOptionsVariable", "programArguments",
//!   "workingDirectory", "env", "unsetEnv"}`. The last four come from the launch of the spec. A run handler needs only
//!   this answer, so a new key is additive, and a launch file names nothing but the command.
//! - `sh`, for the stub of macOS and Linux, which `eval`s it: `DEV_HOME_LAUNCHER=<launcher>` and
//!   `export <ENV>_VM_OPTIONS=<file>`, each value in single quotes.
//! - `cmd`, for the stub of Windows, which `call`s it: two `set "<name>=<value>"` lines.

use anyhow::{Result, bail};

use crate::generation::Materialized;
use crate::spec::LaunchSpec;

/// The variable that names the launcher in the `sh` and `cmd` forms.
pub(crate) const LAUNCHER_VARIABLE: &str = "DEV_HOME_LAUNCHER";

/// The form of the answer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Format {
    Json,
    Sh,
    Cmd,
}

impl Format {
    pub(crate) fn parse(value: &str) -> Result<Self> {
        match value {
            "json" => Ok(Self::Json),
            "sh" => Ok(Self::Sh),
            "cmd" => Ok(Self::Cmd),
            _ => bail!("--format takes json, sh or cmd, got {value:?}"),
        }
    }
}

/// The text of the answer in `format`. Only the `json` form names `launch`, because `bazel run` starts the stub with it.
pub(crate) fn render(materialized: &Materialized, launch: &LaunchSpec, format: Format) -> Result<String> {
    let launcher = materialized.launcher.display().to_string();
    let vm_options = materialized.vm_options_file.display().to_string();
    let variable = &materialized.vm_options_variable;
    match format {
        Format::Json => {
            let value = serde_json::json!({
                "home": materialized.home.display().to_string(),
                "launcher": launcher,
                "vmOptionsFile": vm_options,
                "vmOptionsVariable": variable,
                "programArguments": launch.program_arguments,
                "workingDirectory": launch.working_directory.display().to_string(),
                "env": launch.env,
                "unsetEnv": launch.unset_env,
            });
            Ok(format!("{value}\n"))
        }
        Format::Sh => Ok(format!(
            "{LAUNCHER_VARIABLE}={}\nexport {variable}={}\n",
            sh_quote(&launcher),
            sh_quote(&vm_options)
        )),
        Format::Cmd => Ok(format!(
            "set \"{LAUNCHER_VARIABLE}={}\"\r\nset \"{variable}={}\"\r\n",
            cmd_value(&launcher)?,
            cmd_value(&vm_options)?
        )),
    }
}

/// `value` in single quotes, with each `'` as `'\''`.
pub(crate) fn sh_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

/// `value` for a `set "..."` line. `cmd.exe` expands `%` and ends the quoted value at `"`, so both fail.
fn cmd_value(value: &str) -> Result<&str> {
    if value.contains(['%', '"', '\r', '\n']) {
        bail!("the path {value} has a `%`, a `\"` or a line break, which a `.cmd` file cannot hold. Set INTELLIJ_DEV_HOME_ROOT");
    }
    Ok(value)
}

#[cfg(test)]
mod tests;
