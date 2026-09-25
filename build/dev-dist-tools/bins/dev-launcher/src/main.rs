//! `dev-launcher` starts an IDE from a composed dev distribution, as `bazel run //build:<launcher>` does.
//!
//! The rule `intellij_dev_launcher` (`community/build/intellij_dev.bzl`) writes `<launcher>.launch.json` beside this
//! executable: the Java runtime, the distribution config and the JVM flags. The launcher reads no workspace file to
//! decide a flag. It keeps the dev data outside the workspace through the `out/dev-data` link, which [`devdata`] makes.
//! It links the local home and derives the system properties of the distribution, as `PreBuiltDevMain` does. Then it
//! changes to `BUILD_WORKSPACE_DIRECTORY` and replaces itself with the JVM of the IDE. Thus the IDE runs with the
//! process ID of the launcher.

mod devdata;
mod process;
mod properties;
mod runfiles;

use std::io::Write;
use std::path::{Path, PathBuf};

use component::paths::resolve_relative;
use std::process::Command;

use anyhow::{Context, anyhow, bail};
use indexmap::IndexMap;
use serde::Deserialize;

use crate::properties::{ProductInfo, custom_command, distribution_properties, put_system_property, read_lines};
use crate::runfiles::Runfiles;

/// The class path separator of the JVM on the host.
#[cfg(windows)]
const PATH_LIST_SEPARATOR: &str = ";";
#[cfg(not(windows))]
const PATH_LIST_SEPARATOR: &str = ":";

/// `<launcher>.launch.json`. Every path is a runfiles path except `home`.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct LaunchManifest {
    version: i64,
    java: String,
    ide_config: String,
    #[serde(default)]
    before_run: String,
    jvm_flags: Vec<String>,
    /// The workspace-relative directory below which each launch links its local home. It is below the `out/dev-data`
    /// link, so the home is outside the workspace wherever [`devdata::ensure_dev_data`] makes the link.
    home: String,
}

/// The process that the launcher becomes.
#[derive(Debug)]
struct Launch {
    java: String,
    argv: Vec<String>,
    /// The runfiles variables, on top of the environment of the launcher.
    env: Vec<(&'static str, String)>,
    dir: String,
}

fn main() {
    let args: Vec<String> = match std::env::args_os().map(std::ffi::OsString::into_string).collect() {
        Ok(args) => args,
        Err(arg) => {
            eprintln!("ERROR: an argument is not valid UTF-8: {}", arg.display());
            std::process::exit(1);
        }
    };
    let getenv = |name: &str| std::env::var(name).unwrap_or_default();
    match prepare(&args, &getenv, &mut std::io::stderr()) {
        Ok(launch) => std::process::exit(execute(&launch)),
        Err(error) => {
            eprintln!("ERROR: {error:#}");
            std::process::exit(1);
        }
    }
}

/// Reads the launch manifest and the distribution, and returns the JVM command line. `getenv` returns an empty string
/// for an absent variable.
fn prepare(args: &[String], getenv: &dyn Fn(&str) -> String, warnings: &mut dyn Write) -> anyhow::Result<Launch> {
    let program = args.first().context("no program name in the arguments")?;
    let self_path = path_string(fscopy::absolute_path(Path::new(program))?)?;
    let stem = self_path.strip_suffix(".exe").unwrap_or(&self_path);
    let manifest = read_launch_manifest(&format!("{stem}.launch.json"))?;
    let files = Runfiles::find(&self_path, getenv)?;
    let (wrapper, mut program_args) = parse_wrapper_arguments(&args[1..], getenv)?;
    let workspace = getenv("BUILD_WORKSPACE_DIRECTORY");
    if workspace.is_empty() {
        bail!("BUILD_WORKSPACE_DIRECTORY is not set. Start the launcher with `bazel run`");
    }
    // Before the local home, which would create `out/dev-data` as a directory.
    devdata::ensure_dev_data(Path::new(&workspace), getenv, warnings);
    let expand = |value: &str| expand_braces(value, getenv);

    let mut command_line = wrapper.debug_flags;
    command_line.extend(getenv("JVM_FLAGS").split_whitespace().map(str::to_owned));
    command_line.extend(manifest.jvm_flags.iter().map(|flag| expand(flag)));
    command_line.extend(wrapper.jvm_flags);
    for argument in &mut program_args {
        *argument = expand(argument);
    }

    let config_file = files.rlocation(&manifest.ide_config)?;
    let (distribution_home, mut main_class) = read_ide_config(&config_file)?;
    let layout = Path::new(&distribution_home).join(component::layout::LOCAL_LAYOUT_FILE);
    let home = if layout.exists() {
        link_local_home(&files, &manifest, &layout, &workspace, warnings)?
    } else {
        distribution_home
    };

    let info = ProductInfo::read(Path::new(&home))?;
    let mut properties = distribution_properties(&home, &info)?;
    let mut caller_properties = IndexMap::new();
    for flag in &command_line {
        put_system_property(&mut caller_properties, flag);
    }
    if caller_properties
        .get("idea.dev.mode.custom.command")
        .is_some_and(|value| value.eq_ignore_ascii_case("true"))
    {
        let Some(command) = program_args.first() else {
            bail!("-Didea.dev.mode.custom.command=true needs the command as the first program argument");
        };
        let (command_main_class, command_properties) = custom_command(&home, &info, command)?;
        main_class = command_main_class;
        properties.extend(command_properties);
    }

    let class_path = read_class_path(&home)?;
    let java = files.rlocation(&manifest.java)?;
    let mut argv = vec![java.clone()];
    argv.extend(command_line);
    // `PreBuiltDevMain` sets these three before the properties of the distribution, which can override them.
    argv.extend([
        "-Didea.vendor.name=JetBrains".to_owned(),
        "-Didea.use.dev.build.server=true".to_owned(),
        format!("-Didea.home.path={home}"),
    ]);
    for (key, value) in &properties {
        if caller_properties.contains_key(key) && is_caller_owned_property(key) {
            continue;
        }
        argv.push(format!("-D{key}={value}"));
    }
    argv.extend(["-cp".to_owned(), class_path.join(PATH_LIST_SEPARATOR), main_class]);
    argv.extend(program_args);

    let env = files.environment();
    if !manifest.before_run.is_empty() {
        run_before_run(&files, &manifest.before_run, &env, &workspace)?;
    }
    Ok(Launch {
        java,
        argv,
        env,
        dir: workspace,
    })
}

fn read_launch_manifest(file: &str) -> anyhow::Result<LaunchManifest> {
    let data = std::fs::read(file).with_context(|| format!("read the launch manifest {file}"))?;
    let manifest: LaunchManifest = serde_json::from_slice(&data).with_context(|| format!("read the launch manifest {file}"))?;
    if manifest.version != 1 {
        bail!("unsupported launch manifest version: {}", manifest.version);
    }
    Ok(manifest)
}

/// Reads the `DevIdeConfig` file: the distribution home, relative to the file, and the main class of the IDE.
///
/// Java reads the file as ISO-8859-1, and `java_properties` reads it as windows-1252. Every file that the launcher reads
/// is ASCII, so the difference has no effect.
fn read_ide_config(file: &str) -> anyhow::Result<(String, String)> {
    let data = std::fs::read(file).with_context(|| format!("read {file}"))?;
    let properties = java_properties::read(data.as_slice()).map_err(|error| anyhow!("{file}: {error}"))?;
    let (Some(home), Some(main_class)) = (
        properties.get("home.path").filter(|home| !home.is_empty()),
        properties.get("main.class.name").filter(|main_class| !main_class.is_empty()),
    ) else {
        bail!("{file} states no home.path or no main.class.name");
    };
    let home = if Path::new(home).is_absolute() {
        home.clone()
    } else {
        resolve_relative(component::paths::parent(file), home)
    };
    Ok((home, main_class.clone()))
}

fn read_class_path(home: &str) -> anyhow::Result<Vec<String>> {
    let lines = read_lines(&Path::new(home).join("core-classpath.txt"))?;
    Ok(lines
        .iter()
        .map(|line| line.trim())
        .filter(|line| !line.is_empty())
        .map(|line| {
            if Path::new(line).is_absolute() {
                line.to_owned()
            } else {
                resolve_relative(home, line)
            }
        })
        .collect())
}

/// The properties that the command line of a launcher keeps against the properties of the distribution, as
/// `PreBuiltDevMain.isCallerOwnedProperty` does.
fn is_caller_owned_property(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.starts_with("rider.")
        || lower.starts_with("resharper.")
        || matches!(
            name,
            "idea.platform.prefix" | "idea.suppressed.plugins.set.selector" | "awt.toolkit.name"
        )
}

/// The options of the java stub itself, see `java_stub_template.txt` of rules_java.
#[derive(Debug, Default, PartialEq, Eq)]
struct WrapperArguments {
    debug_flags: Vec<String>,
    jvm_flags: Vec<String>,
}

/// Takes the wrapper options of the java stub: the leading ones up to the first other argument, and
/// `--wrapper_script_flag=<option>` at any position. The Bazel plugin of the IDE debugs a launcher through them.
fn parse_wrapper_arguments(args: &[String], getenv: &dyn Fn(&str) -> String) -> anyhow::Result<(WrapperArguments, Vec<String>)> {
    let mut result = WrapperArguments::default();
    let mut program_args: Vec<String> = Vec::new();
    let mut debug_port = String::new();
    let mut process = |argument: &str| {
        if argument == "--debug" {
            debug_port = getenv("DEFAULT_JVM_DEBUG_PORT");
            if debug_port.is_empty() {
                debug_port = "5005".to_owned();
            }
        } else if let Some(port) = argument.strip_prefix("--debug=") {
            debug_port = port.to_owned();
        } else if let Some(flag) = argument.strip_prefix("--jvm_flag=") {
            result.jvm_flags.push(flag.to_owned());
        } else if let Some(flags) = argument.strip_prefix("--jvm_flags=") {
            result.jvm_flags.extend(flags.split_whitespace().map(str::to_owned));
        } else {
            return false;
        }
        true
    };
    for argument in args {
        if let Some(option) = argument.strip_prefix("--wrapper_script_flag=") {
            if !process(option) {
                bail!("invalid wrapper argument '{argument}'");
            }
        } else if !program_args.is_empty() || !process(argument) {
            program_args.push(argument.clone());
        }
    }
    if !debug_port.is_empty() {
        let mut suspend = getenv("DEFAULT_JVM_DEBUG_SUSPEND");
        if suspend.is_empty() {
            suspend = "y".to_owned();
        }
        result.debug_flags = vec![format!(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend={suspend},address={debug_port}"
        )];
    }
    Ok((result, program_args))
}

/// Replaces each `${NAME}` with the environment variable `NAME`, as the shell expansion of the java stub does for its
/// JVM flags. A bare `$NAME` stays, so a value such as `$APP_PACKAGE` reaches the IDE unchanged.
fn expand_braces(value: &str, getenv: &dyn Fn(&str) -> String) -> String {
    let mut result = String::with_capacity(value.len());
    let mut rest = value;
    while let Some(start) = rest.find("${") {
        let Some(end) = rest[start..].find('}') else {
            break;
        };
        result.push_str(&rest[..start]);
        result.push_str(&getenv(&rest[start + 2..start + end]));
        rest = &rest[start + end + 1..];
    }
    result.push_str(rest);
    result
}

/// Links the local home of the distribution into `<workspace>/<home>/<pid>`, after it removes the homes of the
/// processes that no longer run.
fn link_local_home(
    files: &Runfiles,
    manifest: &LaunchManifest,
    layout: &Path,
    workspace: &str,
    warnings: &mut dyn Write,
) -> anyhow::Result<String> {
    let homes = resolve_relative(workspace, &manifest.home);
    remove_stale_homes(Path::new(&homes), warnings);
    let home = resolve_relative(&homes, &std::process::id().to_string());
    match std::fs::remove_dir_all(&home) {
        Err(error) if error.kind() != std::io::ErrorKind::NotFound => {
            return Err(error).with_context(|| format!("remove {home}"));
        }
        _ => {}
    }
    component::local_home::link_local_home_with(layout, Path::new(&home), &|name| files.lookup().resolve(name))
        .context("cannot prepare the local dev home")?;
    Ok(home)
}

/// Deletes each home below `homes` whose process no longer runs.
fn remove_stale_homes(homes: &Path, warnings: &mut dyn Write) {
    let Ok(entries) = std::fs::read_dir(homes) else {
        return;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(pid) = name.to_str().and_then(|name| name.parse::<u32>().ok()) else {
            continue;
        };
        let directory = entry.file_type().is_ok_and(|file_type| file_type.is_dir());
        if !directory || pid == std::process::id() || process::runs(pid) {
            continue;
        }
        if let Err(error) = std::fs::remove_dir_all(entry.path()) {
            let _ = writeln!(warnings, "WARNING: cannot remove the stale dev home {pid}: {error}");
        }
    }
}

/// Runs the before-run executable, a runfile, in the workspace, and fails when it fails.
fn run_before_run(files: &Runfiles, executable: &str, env: &[(&'static str, String)], workspace: &str) -> anyhow::Result<()> {
    let program = files.rlocation(executable)?;
    let status = Command::new(&program)
        .envs(env.iter().map(|(name, value)| (name, value)))
        .current_dir(workspace)
        .status()
        .with_context(|| format!("the before-run step {executable} failed"))?;
    if !status.success() {
        bail!("the before-run step {executable} failed: {status}");
    }
    Ok(())
}

/// Replaces this process with the JVM of the IDE. Where a process cannot replace itself, it runs the JVM as a child
/// and returns its exit code.
fn execute(launch: &Launch) -> i32 {
    if let Err(error) = std::env::set_current_dir(&launch.dir) {
        eprintln!("ERROR: {}: {error}", launch.dir);
        return 1;
    }
    let mut command = Command::new(&launch.java);
    command
        .args(&launch.argv[1..])
        .envs(launch.env.iter().map(|(name, value)| (name, value)));
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        let error = command.exec();
        eprintln!("ERROR: cannot start {}: {error}", launch.java);
        1
    }
    #[cfg(not(unix))]
    {
        match command.status() {
            Ok(status) => status.code().unwrap_or(1),
            Err(error) => {
                eprintln!("ERROR: cannot start {}: {error}", launch.java);
                1
            }
        }
    }
}

fn path_string(path: PathBuf) -> anyhow::Result<String> {
    path.into_os_string()
        .into_string()
        .map_err(|path| anyhow!("the path is not valid UTF-8: {}", path.display()))
}

#[cfg(test)]
mod main_tests;
