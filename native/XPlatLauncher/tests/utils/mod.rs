// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::{env, fs, io, thread, time};
use std::collections::HashMap;
use std::fs::File;
use std::io::{BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Output};
use std::sync::Once;
use std::time::SystemTime;
use log::debug;
use utils::{get_path_from_env_var, PathExt};

static INIT: Once = Once::new();
static mut SHARED: Option<TestEnvironmentShared> = None;

pub struct TestEnvironment {
    pub launcher_path: PathBuf,
    pub test_root_dir: tempfile::TempDir
}

pub fn prepare_test_env(layout_kind: &LayoutSpecification) -> TestEnvironment {
    let result = match prepare_test_env_impl(layout_kind) {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to prepare test environment: {e:?}")
        }
    };

    result
}

fn prepare_test_env_impl(layout_kind: &LayoutSpecification) -> Result<TestEnvironment> {
    INIT.call_once(|| {
        let shared = init_test_environment_once().expect("Failed to init shared test environment");
        unsafe {
            SHARED = Some(shared)
        }
    });

    let shared = unsafe { SHARED.as_ref() }.expect("Shared test environment should have already been initialized");

    let os = env::consts::OS;
    let product_info_relative = format!("resources/product_info_{os}.json");
    let product_info_path = shared.project_root.join(product_info_relative);

    // create tmp dir
    let temp_dir = tempfile::tempdir().context(format!("Failed to create temp directory"))?;
    let temp_dir_path = temp_dir.path();

    resolve_java_source_type(&shared.jbrsdk_root, layout_kind);

    layout_launcher(
        layout_kind,
        &temp_dir_path,
        &shared.project_root,
        &shared.jbrsdk_root,
        &shared.intellij_app_jar_source,
        &product_info_path,
    )?;

    let launcher_dir = resolve_launcher_dir(&temp_dir_path, layout_kind);
    env::set_current_dir(&launcher_dir)?;

    let launcher_path = launcher_dir.join("xplat-launcher");

    let result = TestEnvironment {
        launcher_path,
        test_root_dir: temp_dir
    };

    Ok(result)
}

pub fn init_test_environment_once() -> Result<TestEnvironmentShared> {
    let project_root = env::current_dir().expect("Failed to get project root");

    // gradle_command_wrapper("clean");
    gradle_command_wrapper("fatJar");

    let gradle_jvm = project_root
        .join("resources")
        .join("TestProject")
        .join("gradle-jvm");

    // TODO: remove after wrapper with https://github.com/mfilippov/gradle-jvm-wrapper/pull/31
    let java_dir_prefix = match env::consts::OS {
        "windows" => { "jdk" }
        _ => { "jbrsdk" }
    };

    // jbrsdk-17.0.3-osx-x64-b469.37-f87880
    let jbrsdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix)?;

    // jbrsdk-17.0.3-x64-b469
    let jbrsdk_root = get_child_dir(&jbrsdk_gradle_parent, java_dir_prefix)?;

    let jar_path = Path::new("./resources/TestProject/build/libs/app.jar");
    let intellij_app_jar_source = jar_path.canonicalize()?;

    let result = TestEnvironmentShared {
        project_root,
        jbrsdk_root,
        intellij_app_jar_source
    };

    Ok(result)
}

pub struct TestEnvironmentShared {
    project_root: PathBuf,
    jbrsdk_root: PathBuf,
    intellij_app_jar_source: PathBuf
}

pub fn gradle_command_wrapper(gradle_command: &str) {
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

#[cfg(any(target_os = "macos", target_os = "linux"))]
fn get_gradlew_executable_name() -> String {
    "gradlew".to_string()
}

fn command_handler(command: &Output) {
    let exit_status = command.status;
    let stdout = String::from_utf8_lossy(&command.stdout);
    let stderr = String::from_utf8_lossy(&command.stderr);

    if !exit_status.success() {
        let message = format!("Command didn't succeed,\n exit code: {exit_status},\n stdout: {stdout},\n stderr: {stderr}");
        panic!("{message}")
    }
}

pub fn get_child_dir(parent: &Path, prefix: &str) -> io::Result<PathBuf> {
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

pub enum LayoutSpecification {
    LauncherLocationMainBinJavaIsEnvVar,
    LauncherLocationMainBinJavaIsUserJRE,
    LauncherLocationMainBinJavaIsJBR,
    LauncherLocationMainBinJavaIsJavaHome,

    LauncherLocationPluginsBinJavaIsEnvVar,
    LauncherLocationPluginsBinJavaIsUserJRE,
    LauncherLocationPluginsBinJavaIsJBR,
    LauncherLocationPluginsBinJavaIsJavaHome,
}

#[cfg(target_os = "linux")]
pub fn layout_launcher(
    layout_kind: &LayoutSpecification,
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
    // linux:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── plugins/
    // │   └── remote-dev-server
    // │       └── bin
    // │           └── xplat-launcher
    // ├── jbr/
    // └── product-info.json

    let launcher = &get_testing_binary_root(project_root).join("xplat-launcher");
    if !launcher.exists() {
        bail!("Didn't find source launcher to layout, expected path: {launcher:?}");
    }

    layout_launcher_impl(
        layout_kind,
        target_dir,
        vec![
            "bin/idea64.vmoptions",
            "lib/test.jar"
        ],
        vec![
            (launcher.as_path(), "bin/xplat-launcher"),
            (launcher.as_path(), "plugins/remote-dev-server/bin/xplat-launcher"),
            (intellij_main_mock_jar, "lib/app.jar"),
            (product_info_absolute_path, "product-info.json"),
        ],
        vec![
            (jbr_absolute_path, "jbr")
        ]
    )?;

    Ok(())
}

#[cfg(target_os = "macos")]
pub fn layout_launcher(
    layout_kind: &LayoutSpecification,
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
    // macos:
    // .
    // └── Contents
    //     ├── bin/
    //     │   └── xplat-launcher
    //     │   └── idea.vmoptions
    //     ├── Resources/
    //     │   └── product-info.json
    //     ├── lib/
    //     │   └── app.jar
    //     ├── plugins/
    //     │   └── remote-dev-server
    //     │       └── bin
    //     │           └── xplat-launcher
    //     └── jbr/

    let launcher = get_testing_binary_root(project_root).join("xplat-launcher");
    if !launcher.exists() {
        bail!("Didn't find source launcher to layout, expected path: {launcher:?}");
    }

    layout_launcher_impl(
        layout_kind,
        target_dir,
        vec![
            "Contents/bin/idea.vmoptions",
            "Contents/lib/test.jar"
        ],
        vec![
            (launcher.as_path(), "Contents/bin/xplat-launcher"),
            (launcher.as_path(), "Contents/plugins/remote-dev-server/bin/xplat-launcher"),
            (intellij_main_mock_jar, "Contents/lib/app.jar"),
            (product_info_absolute_path, "Contents/Resources/product-info.json"),
        ],
        vec![
            (jbr_absolute_path, "Contents/jbr")
        ]
    )?;

    Ok(())
}

#[cfg(target_os = "windows")]
pub fn layout_launcher(
    layout_kind: &LayoutSpecification,
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
    // windows:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.exe.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── jbr/
    // ├── plugins/
    // │   └── remote-dev-server
    // │       └── bin
    // │           └── xplat-launcher
    // └── product-info.json

    let launcher = get_testing_binary_root(project_root).join("xplat-launcher.exe");
    if !launcher.exists() {
        bail!("Didn't find source launcher to layout, expected path: {launcher:?}");
    }

    layout_launcher_impl(
        layout_kind,
        target_dir,
        vec![
            "bin/idea64.exe.vmoptions",
            "lib/test.jar"
        ],
        vec![
            (launcher.as_path(), "bin/xplat-launcher.exe"),
            (launcher.as_path(), "plugins/remote-dev-server/bin/xplat-launcher.exe"),
            (intellij_main_mock_jar, "lib/app.jar"),
            (product_info_absolute_path, "product-info.json"),
        ],
        vec![
            (jbr_absolute_path, "jbr")
        ]
    )?;

    Ok(())
}

fn get_testing_binary_root(project_root: &Path) -> PathBuf {
    match get_path_from_env_var("XPLAT_LAUNCHER_TESTS_TARGET_BINARY_ROOT") {
        Ok(x) => x,
        Err(_) => {
            let target = match cfg!(debug_assertions) {
                true => { "debug".to_string() }
                false => { "release".to_string() }
            };

            project_root
                .join("target")
                .join(target)
        }
    }
}

fn layout_launcher_impl(
    layout_kind: &LayoutSpecification,
    target_dir: &Path,
    create_files: Vec<&str>,
    copy_files: Vec<(&Path, &str)>,
    symlink_dirs: Vec<(&Path, &str)>
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

    for (source, target_relative) in symlink_dirs {
        match layout_kind {
            LayoutSpecification::LauncherLocationMainBinJavaIsJBR
            | LayoutSpecification::LauncherLocationPluginsBinJavaIsJBR => {
                let target = &target_dir.join(target_relative);
                fs::create_dir_all(target.parent_or_err()?)?;
                symlink(source, target).context(format!("Failed to create symlink {target:?} pointing to {source:?}"))?;
            }
            _ => break
        }
    }

    Ok(())
}

#[cfg(any(target_os = "macos", target_os = "linux"))]
fn symlink(source: &Path, target: &Path) -> Result<()> {
    std::os::unix::fs::symlink(source, target)
        .context(format!("Failed to create symlink {target:?} pointing to {source:?}"))?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn symlink(source: &Path, target: &Path) -> Result<()> {
    junction::create(source, target)
        .context(format!("Failed to create junction {target:?} pointing to {source:?}"))?;

    Ok(())
}

pub fn resolve_java_source_type(java_root: &PathBuf, layout_kind: &LayoutSpecification) {
    match layout_kind {
        LayoutSpecification::LauncherLocationMainBinJavaIsEnvVar
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsEnvVar => {
            env::set_var("IU_JDK", java_root);
            debug!("IU_JDK set. JBR is not included in layout")
        }
        LayoutSpecification::LauncherLocationMainBinJavaIsUserJRE
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsUserJRE => {
            create_dummy_config(java_root);
            debug!("Custom user file with runtime is created. JBR is not included in layout")
        },
        LayoutSpecification::LauncherLocationMainBinJavaIsJBR
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsJBR => {
            debug!("JBR is included in test layout")
        }
        LayoutSpecification::LauncherLocationMainBinJavaIsJavaHome
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsJavaHome => todo!()
    }
}

pub fn resolve_launcher_dir(test_dir: &Path, layout_kind: &LayoutSpecification) -> PathBuf {
    let root = match cfg!(target_os = "macos") {
        true => test_dir.join("Contents"),
        false => test_dir.to_path_buf()
    };

    match layout_kind {
        LayoutSpecification::LauncherLocationMainBinJavaIsEnvVar
        | LayoutSpecification::LauncherLocationMainBinJavaIsUserJRE
        | LayoutSpecification::LauncherLocationMainBinJavaIsJBR
        | LayoutSpecification::LauncherLocationMainBinJavaIsJavaHome => root.join("bin"),

        LayoutSpecification::LauncherLocationPluginsBinJavaIsEnvVar
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsUserJRE
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsJBR
        | LayoutSpecification::LauncherLocationPluginsBinJavaIsJavaHome => root.join("plugins/remote-dev-server/bin")
    }
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
pub fn get_jbr_home(jbr_dir: &PathBuf) -> PathBuf {
    jbr_dir.canonicalize().unwrap()
}

#[cfg(target_os = "macos")]
pub fn get_jbr_home(jbr_dir: &PathBuf) -> PathBuf {
    jbr_dir.join("Contents").join("Home").canonicalize().unwrap()
}

#[cfg(target_os = "windows")]
pub fn get_jbr_home(jbr_dir: &PathBuf) -> PathBuf {
    junction::get_target(jbr_dir).unwrap()
}

#[cfg(any(target_os = "macos", target_os = "linux"))]
fn get_config_home() -> PathBuf {
    let home = env::var("HOME").unwrap();

    Path::new(&home).join(".config")
}

#[cfg(target_os = "windows")]
pub fn get_config_home() -> PathBuf {
    PathBuf::from("C:\\tmp")
}

pub fn create_dummy_config(java_root: &PathBuf) {
    let idea_config_path = get_custom_user_file_with_java_path();
    let idea_config_file = idea_config_path.join("idea.jdk");

    if !idea_config_file.exists() {
        fs::create_dir_all(idea_config_path).unwrap();
        let idea_jdk_content = java_root.to_str().unwrap().as_bytes();
        let mut idea_jdk_file = File::create(&idea_config_file).expect("Fail to create idea.jdk");
        idea_jdk_file.write_all(idea_jdk_content).expect("Fail to write in idea.jdk");
    }
}

pub fn get_custom_user_file_with_java_path() -> PathBuf {
    let config_home_path = get_config_home();
    config_home_path
        .join("JetBrains")
        .join("IntellijIdea2022.3") //todo
}

// TODO: test for additionalJvmArguments in product-info.json being set
// (e.g. "-Didea.vendor.name=JetBrains")

pub struct LauncherRunResult {
    pub exit_status: ExitStatus,
    pub dump: Option<IntellijMainDumpedLaunchParameters>
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct IntellijMainDumpedLaunchParameters {
    pub cmdArguments: Vec<String>,
    pub vmOptions: Vec<String>,
    pub environmentVariables: HashMap<String, String>,
    pub systemProperties: HashMap<String, String>
}

pub fn run_launcher_with_default_args(test: &TestEnvironment, args: &[&str]) -> LauncherRunResult {
    let output_file = test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME);
    let output_args = ["dump-launch-parameters", "--output", &output_file.to_string_lossy()];
    let full_args = &mut output_args.to_vec();
    full_args.append(&mut args.to_vec());

    let result = match run_launcher_impl(test, full_args, &output_file) {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to get launcher run result: {e:?}")
        }
    };

    result
}

pub fn run_launcher(test: &TestEnvironment, args: &[&str], output_file: &Path) -> LauncherRunResult {
    let result = match run_launcher_impl(test, args, output_file) {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to get launcher run result: {e:?}")
        }
    };

    result
}

pub const TEST_OUTPUT_FILE_NAME: &str = "output.json";

fn run_launcher_impl(test: &TestEnvironment, args: &[&str], output_file: &Path) -> Result<LauncherRunResult> {
    let mut launcher_process = Command::new(&test.launcher_path)
        .current_dir(&test.test_root_dir)
        .args(args)
        .env(xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1")
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
                    dump: match es.success() {
                        true => Some(read_launcher_run_result(&output_file)?),
                        false => None
                    }
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

    let reader = BufReader::new(file);
    let dump: IntellijMainDumpedLaunchParameters = serde_json::from_reader(reader)?;
    Ok(dump)
}

pub fn run_launcher_and_get_dump(layout_kind: &LayoutSpecification) -> IntellijMainDumpedLaunchParameters {
    let test = prepare_test_env(layout_kind);
    let result = run_launcher_with_default_args(&test, &[]);
    assert!(result.exit_status.success(), "Launcher didn't exit successfully");
    result.dump.expect("Launcher exited successfully, but there is no output")
}

pub fn run_launcher_and_get_dump_with_args(layout_kind: &LayoutSpecification, args: &[&str]) -> IntellijMainDumpedLaunchParameters {
    let test = prepare_test_env(layout_kind);
    let result = run_launcher_with_default_args(&test, args);
    assert!(result.exit_status.success(), "Launcher didn't exit successfully");
    result.dump.expect("Launcher exited successfully, but there is no output")
}