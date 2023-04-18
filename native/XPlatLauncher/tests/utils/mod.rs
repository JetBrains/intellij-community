// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::{env, fs, io, thread, time};
use std::collections::HashMap;
use std::fmt::{Debug, Formatter};
use std::fs::File;
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Output, Stdio};
use std::sync::Once;

use anyhow::{bail, Context, Result};
use log::debug;
use serde::{Deserialize, Serialize};
use utils::PathExt;
use xplat_launcher::get_config_home;

static INIT: Once = Once::new();
static mut SHARED: Option<TestEnvironmentShared> = None;

#[derive(Clone)]
pub enum LauncherLocation { Standard, RemoteDev }

pub struct TestEnvironment<'a> {
    pub dist_root: PathBuf,
    pub project_dir: PathBuf,
    launcher_path: PathBuf,
    shared_env: &'a TestEnvironmentShared,
    test_root_dir: tempfile::TempDir,
    to_delete: Vec<PathBuf>
}

impl<'a> TestEnvironment<'a> {
    pub fn create_jbr_link(&self, link_name: &str) -> PathBuf {
        let link = self.dist_root.join(link_name);
        symlink(&self.shared_env.jbr_root, &link).unwrap();
        link
    }

    pub fn create_temp_dir(&self, relative_path: &str) -> PathBuf {
        let temp_dir = self.test_root_dir.path().join(relative_path);
        fs::create_dir_all(&temp_dir).expect(format!("Failed to create temp. dir: {:?}", temp_dir).as_str());
        temp_dir
    }

    pub fn delete_later(&mut self, path: &Path) {
        self.to_delete.push(path.to_path_buf());
    }
}

impl<'a> Drop for TestEnvironment<'a> {
    fn drop(&mut self) {
        for path in &self.to_delete {
            debug!("Deleting {:?}", path);
            if let Err(e) = fs::remove_dir_all(&path) {
                eprintln!("{:?}", e);
            }
        }
    }
}

pub fn prepare_test_env<'a>(launcher_location: LauncherLocation) -> TestEnvironment<'a> {
    match prepare_test_env_impl(launcher_location) {
        Ok(x) => x,
        Err(e) => panic!("Failed to prepare test environment: {:?}", e),
    }
}

struct TestEnvironmentShared {
    launcher_path: PathBuf,
    jbr_root: PathBuf,
    app_jar_path: PathBuf,
    product_info_path: PathBuf
}

fn prepare_test_env_impl<'a>(launcher_location: LauncherLocation) -> Result<TestEnvironment<'a>> {
    INIT.call_once(|| {
        let shared = init_test_environment_once().expect("Failed to init shared test environment");
        unsafe {
            SHARED = Some(shared)
        }
    });

    let shared_env = unsafe { SHARED.as_ref() }.expect("Shared test environment should have already been initialized");

    let temp_dir = tempfile::Builder::new().prefix("xplat_launcher_test_").tempdir().context(format!("Failed to create temp directory"))?;

    let (dist_root, launcher_path) = layout_launcher(launcher_location, &temp_dir.path(), &shared_env)?;

    let project_dir = temp_dir.path().join("_project");
    fs::create_dir_all(&project_dir)?;

    env::set_current_dir(&launcher_path.parent().unwrap())?;

    // clean environment variables
    env::remove_var("IU_JDK");
    env::remove_var("JDK_HOME");
    env::remove_var("JAVA_HOME");

    Ok(TestEnvironment { dist_root, project_dir, launcher_path, shared_env, test_root_dir: temp_dir, to_delete: Vec::new() })
}

fn init_test_environment_once() -> Result<TestEnvironmentShared> {
    let project_root = env::current_dir().expect("Failed to get project root");

    let build_target = if cfg!(debug_assertions) { "debug" } else { "release" };
    let bin_name = if cfg!(target = "windows") { "xplat-launcher.exe" } else { "xplat-launcher" };
    let launcher_path = project_root.join("target").join(build_target).join(bin_name);
    if !launcher_path.exists() {
        bail!("Didn't find source launcher to layout, expected path: {:?}", launcher_path);
    }

    // gradle_command_wrapper("clean");
    gradle_command_wrapper("fatJar");

    let jbr_root = get_jbr_sdk_from_project_root(&project_root)?;

    let app_jar_path = Path::new("./resources/TestProject/build/libs/app.jar").canonicalize()?;

    let product_info_path = project_root.join(format!("resources/product_info_{}.json", env::consts::OS));

    Ok(TestEnvironmentShared { launcher_path, jbr_root, app_jar_path, product_info_path })
}

fn get_jbr_sdk_from_project_root(project_root: &Path) -> Result<PathBuf> {
    let gradle_jvm = project_root.join("resources").join("TestProject").join("gradle-jvm");

    // TODO: remove after wrapper with https://github.com/mfilippov/gradle-jvm-wrapper/pull/31
    let java_dir_prefix = if env::consts::OS == "windows" { "jdk" } else { "jbrsdk" };

    // jbrsdk-17.0.3-osx-x64-b469.37-f87880
    let sdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix)?;

    // jbrsdk-17.0.3-x64-b469
    let sdk_root = get_child_dir(&sdk_gradle_parent, java_dir_prefix)?;

    Ok(sdk_root)
}

fn gradle_command_wrapper(gradle_command: &str) {
    let current_dir = env::current_dir()
        .expect("Failed to get current dir")
        .canonicalize()
        .expect("Failed to get canonical path to current dir");
    println!("current_dir={current_dir:?}");

    let executable_name = get_gradlew_executable_name();
    let executable = PathBuf::from("./resources/TestProject")
        .join(executable_name);

    assert!(executable.exists());

    let gradlew = executable.canonicalize().expect("Failed to get canonical path to gradlew");
    let command_to_execute = Command::new(gradlew)
        .arg(gradle_command)
        .current_dir("./resources/TestProject")
        .output()
        .expect(format!("Failed to execute gradlew :{gradle_command}").as_str());

    command_handler(&command_to_execute);
}

#[cfg(target_os = "windows")]
fn get_gradlew_executable_name() -> String {
    "gradlew.bat".to_string()
}

#[cfg(target_family = "unix")]
fn get_gradlew_executable_name() -> String {
    "gradlew".to_string()
}

fn command_handler(command: &Output) {
    let exit_status = command.status;
    let stdout = String::from_utf8_lossy(&command.stdout);
    let stderr = String::from_utf8_lossy(&command.stderr);

    if !exit_status.success() {
        let message = format!("Command didn't succeed,\n exit code: {exit_status},\n stdout: {stdout},\n stderr: {stderr}");
        panic!("{}", message)
    }
}

fn get_child_dir(parent: &Path, prefix: &str) -> io::Result<PathBuf> {
    let read_dir = fs::read_dir(parent)?;

    for dir_entry in read_dir {
        let dir_entry_ok = dir_entry?;
        if dir_entry_ok
            .file_name()
            .to_string_lossy()
            .starts_with(prefix)
        {
            return Ok(dir_entry_ok.path());
        }
    }

    return Err(io::Error::new(
        io::ErrorKind::NotFound,
        "Child dir not found",
    ));
}

#[cfg(target_os = "linux")]
fn layout_launcher(launcher_location: LauncherLocation, target_dir: &Path, shared_env: &TestEnvironmentShared) -> Result<(PathBuf, PathBuf)> {
    // .
    // ├── bin/
    // │   └── xplat-launcher | remote-dev-server
    // │   └── idea64.vmoptions
    // │   └── idea.properties
    // ├── lib/
    // │   └── app.jar
    // │   └── test.jar
    // ├── jbr/
    // └── product-info.json

    let launcher_rel_path = match layout_spec.launcher_location {
        LauncherLocation::Standard => "bin/xplat-launcher",
        LauncherLocation::RemoteDev => "bin/remote-dev-server"
    };

    layout_launcher_impl(
        target_dir,
        vec![
            "bin/idea.properties",
            "bin/idea64.vmoptions",
            "lib/test.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.app_jar_path, "lib/app.jar"),
            (&shared_env.product_info_path, "product-info.json")
        ],
        &shared_env.jbr_root
    )?;

    Ok((target_dir.to_path_buf(), target_dir.join(launcher_rel_path)))
}

#[cfg(target_os = "macos")]
fn layout_launcher(launcher_location: LauncherLocation, target_dir: &Path, shared_env: &TestEnvironmentShared) -> Result<(PathBuf, PathBuf)> {
    // .
    // └── Contents
    //     ├── bin/
    //     │   └── xplat-launcher | remote-dev-server
    //     │   └── idea.vmoptions
    //     │   └── idea.properties
    //     ├── Resources/
    //     │   └── product-info.json
    //     ├── lib/
    //     │   └── app.jar
    //     │   └── test.jar
    //     └── jbr/

    let launcher_rel_path = match launcher_location {
        LauncherLocation::Standard => "bin/xplat-launcher",
        LauncherLocation::RemoteDev => "bin/remote-dev-server"
    };
    let dist_root = target_dir.join("Contents");

    layout_launcher_impl(
        &dist_root,
        vec![
            "bin/idea.properties",
            "bin/idea.vmoptions",
            "lib/test.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.app_jar_path, "lib/app.jar"),
            (&shared_env.product_info_path, "Resources/product-info.json")
        ],
        &shared_env.jbr_root
    )?;

    let launcher_path = dist_root.join(launcher_rel_path);
    Ok((dist_root, launcher_path))
}

#[cfg(target_os = "windows")]
fn layout_launcher(launcher_location: LauncherLocation, target_dir: &Path, shared_env: &TestEnvironmentShared) -> Result<(PathBuf, PathBuf)> {
    // .
    // ├── bin/
    // │   └── xplat-launcher.exe | remote-dev-server.exe
    // │   └── idea64.exe.vmoptions
    // │   └── idea.properties
    // ├── lib/
    // │   └── app.jar
    // │   └── test.jar
    // ├── jbr/
    // └── product-info.json

    let launcher_rel_path = match launcher_location {
        LauncherLocation::Standard => "bin\\xplat-launcher.exe",
        LauncherLocation::RemoteDev => "bin\\remote-dev-server.exe"
    };

    layout_launcher_impl(
        target_dir,
        vec![
            "bin\\idea.properties",
            "bin\\idea64.exe.vmoptions",
            "lib\\test.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.app_jar_path, "lib\\app.jar"),
            (&shared_env.product_info_path, "product-info.json")
        ],
        &shared_env.jbr_root
    )?;

    Ok((target_dir.to_path_buf(), target_dir.join(launcher_rel_path)))
}

fn layout_launcher_impl(
    target_dir: &Path,
    create_files: Vec<&str>,
    copy_files: Vec<(&Path, &str)>,
    jbr_path: &Path
) -> Result<()> {
    for file in create_files {
        let target = &target_dir.join(file);
        fs::create_dir_all(target.parent_or_err()?)?;
        File::create(target).context(format!("Failed to create file {target:?}"))?;
    }

    for (source, target_relative) in copy_files {
        let target = &target_dir.join(target_relative);
        fs::create_dir_all(target.parent_or_err()?)?;
        fs::copy(source, target).context(format!("Failed to copy from {source:?} to {target:?}"))?;
    }

    symlink(jbr_path, &target_dir.join("jbr"))?;

    Ok(())
}

#[cfg(target_family = "unix")]
fn symlink(original: &Path, link: &Path) -> Result<()> {
    std::os::unix::fs::symlink(original, link)
        .context(format!("Failed to create symlink {link:?} pointing to {original:?}"))?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn symlink(target: &Path, junction: &Path) -> Result<()> {
    junction::create(target, junction)
        .context(format!("Failed to create junction {junction:?} pointing to {target:?}"))?;

    Ok(())
}

#[cfg(target_os = "linux")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("bin")
        .join("java")
}

#[cfg(target_os = "windows")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("bin")
        .join("java.exe")
}

#[cfg(target_os = "macos")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("Contents")
        .join("Home")
        .join("bin")
        .join("java")
}

#[cfg(target_os = "linux")]
pub fn get_jbr_home(jbr_dir: &PathBuf) -> Result<PathBuf> {
    Ok(jbr_dir.canonicalize()?)
}

#[cfg(target_os = "macos")]
pub fn get_jbr_home(jbr_dir: &PathBuf) -> Result<PathBuf> {
    Ok(jbr_dir.join("Contents").join("Home").canonicalize()?)
}

#[cfg(target_os = "windows")]
pub fn get_jbr_home(jbr_dir: &PathBuf) -> Result<PathBuf> {
    Ok(junction::get_target(jbr_dir)?)
}

pub fn get_custom_config_dir() -> PathBuf {
    get_config_home().unwrap().join("JetBrains").join("IntelliJIdea2022.3")
}

pub struct LauncherRunSpec {
    location: LauncherLocation,
    dump: bool,
    assert_status: bool,
    args: Vec<String>,
    env: HashMap<String, String>
}

impl LauncherRunSpec {
    pub fn standard() -> LauncherRunSpec {
        LauncherRunSpec {
            location: LauncherLocation::Standard, dump: false, assert_status: false, args: Vec::new(), env: HashMap::new()
        }
    }

    pub fn remote_dev() -> LauncherRunSpec {
        LauncherRunSpec {
            location: LauncherLocation::RemoteDev, dump: false, assert_status: false, args: Vec::new(), env: HashMap::new()
        }
    }

    pub fn with_dump(&mut self) -> &mut Self {
        self.dump = true;
        self
    }

    pub fn assert_status(&mut self) -> &mut Self {
        self.assert_status = true;
        self
    }

    pub fn with_args(&mut self, args: &[&str]) -> &mut Self {
        self.args.extend(args.iter().map(|s| s.to_string()));
        self
    }

    pub fn with_env(&mut self, env: &HashMap<&str, &str>) -> &mut Self {
        self.env.extend(env.iter().map(|(k, v)| (k.to_string(), v.to_string())));
        self
    }
}

pub struct LauncherRunResult {
    pub exit_status: ExitStatus,
    pub stdout: String,
    pub stderr: String,
    dump: Option<Result<IntellijMainDumpedLaunchParameters>>,
}

impl LauncherRunResult {
    pub fn dump(self) -> IntellijMainDumpedLaunchParameters {
        let err_message = format!("Dump was not collected: {:?}", self);
        match self.dump {
            Some(result) => result.expect(err_message.as_str()),
            None => panic!("Dump was not requested; add `.with_dump()` to the run specification")
        }
    }
}

impl Debug for LauncherRunResult {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        f.write_fmt(format_args!(
            "\n** exit code: {}\n** stderr: <<<{}>>>\n** stdout: <<<{}>>>",
            self.exit_status.code().unwrap_or(-1), self.stderr, self.stdout))
    }
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct IntellijMainDumpedLaunchParameters {
    pub cmdArguments: Vec<String>,
    pub vmOptions: Vec<String>,
    pub environmentVariables: HashMap<String, String>,
    pub systemProperties: HashMap<String, String>
}

pub fn run_launcher(run_spec: &LauncherRunSpec) -> LauncherRunResult {
    let test_env = prepare_test_env(run_spec.location.clone());
    run_launcher_ext(&test_env, run_spec)
}

pub fn run_launcher_ext(test_env: &TestEnvironment, run_spec: &LauncherRunSpec) -> LauncherRunResult {
    match run_launcher_impl(&test_env, &run_spec) {
        Ok(result) => {
            if run_spec.assert_status {
                assert!(result.exit_status.success(), "The exit status of the launcher is not successful: {:?}", result);
            }
            result
        }
        Err(e) => {
            panic!("Failed to get launcher run result: {:?}", e)
        }
    }
}

fn run_launcher_impl(test_env: &TestEnvironment, run_spec: &LauncherRunSpec) -> Result<LauncherRunResult> {
    println!("Starting {:?} with args {:?}", &test_env.launcher_path, run_spec.args);

    let stdout_file_path = test_env.test_root_dir.path().join("out.txt");
    let stderr_file_path = test_env.test_root_dir.path().join("err.txt");
    let project_dir = test_env.project_dir.to_str().unwrap();
    let dump_file_path = test_env.test_root_dir.path().join("output.json");
    let dump_file_path_str = dump_file_path.to_string_lossy();

    let mut full_args = Vec::<&str>::new();
    if run_spec.dump {
        let dump_args = match run_spec.location {
            LauncherLocation::Standard => vec!["dump-launch-parameters", "--output", &dump_file_path_str],
            LauncherLocation::RemoteDev => vec!["dumpLaunchParameters", &project_dir, "--output", &dump_file_path_str]
        };
        for arg in dump_args {
            full_args.push(arg);
        }
    }
    for arg in run_spec.args.iter() {
        full_args.push(arg);
    }

    let mut full_env = match run_spec.location {
        LauncherLocation::Standard => HashMap::from([
            (xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1"),
            (xplat_launcher::VERBOSE_LOGGING_ENV_VAR, "1")
        ]),
        LauncherLocation::RemoteDev => HashMap::from([
            (xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1"),
            ("CWM_NO_PASSWORD", "1"),
            ("CWM_HOST_PASSWORD", "1"),
            ("REMOTE_DEV_NON_INTERACTIVE", "1"),
            ("IJ_HOST_CONFIG_DIR", project_dir),
            ("IJ_HOST_SYSTEM_DIR", project_dir),
            ("IJ_HOST_LOGS_DIR", project_dir)
        ]),
    };
    for (k, v) in run_spec.env.iter() {
        full_env.insert(k, v);
    }

    let mut launcher_process = Command::new(&test_env.launcher_path)
        .current_dir(&test_env.test_root_dir)
        .args(full_args)
        .stdout(Stdio::from(File::create(&stdout_file_path)?))
        .stderr(Stdio::from(File::create(&stderr_file_path)?))
        .envs(full_env)
        .spawn()
        .context("Failed to spawn launcher process")?;

    let started = time::Instant::now();

    loop {
        let elapsed = time::Instant::now() - started;
        if elapsed > time::Duration::from_secs(60) {
            panic!("Launcher has been running for more than 60 seconds, terminating")
        }

        match launcher_process.try_wait() {
            Ok(opt) => match opt {
                None => {
                    println!("Waiting for launcher process to exit");
                }
                Some(es) => return Ok(LauncherRunResult {
                    exit_status: es,
                    stdout: fs::read_to_string(&stdout_file_path).context("Cannot open stdout file")?,
                    stderr: fs::read_to_string(&stderr_file_path).context("Cannot open stderr file")?,
                    dump: if run_spec.dump { Some(read_launcher_run_result(&dump_file_path)) } else { None }
                }),
            },
            Err(e) => {
                Err(e)?
            }
        };

        thread::sleep(time::Duration::from_secs(1))
    }
}

fn read_launcher_run_result(path: &Path) -> Result<IntellijMainDumpedLaunchParameters> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut text = String::new();
    reader.read_to_string(&mut text)?;
    let dump: IntellijMainDumpedLaunchParameters = serde_json::from_str(text.as_str())?;
    Ok(dump)
}
