// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::{env, fs, io, thread, time};
use std::collections::HashMap;
use std::fs::{create_dir, File};
use std::io::BufReader;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Output};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Once;
use std::time::SystemTime;
use utils::{get_path_from_env_var, PathExt};

static INIT: Once = Once::new();
static mut SHARED: Option<TestEnvironmentShared> = None;

pub struct TestEnvironment {
    pub launcher_path: PathBuf,
    pub test_root_dir: PathBuf
}

pub fn prepare_test_env(layout_kind: &LauncherLocation) -> TestEnvironment {
    let result = match prepare_test_env_impl(layout_kind) {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to prepare test environment: {e:?}")
        }
    };

    result
}

fn prepare_test_env_impl(layout_kind: &LauncherLocation) -> Result<TestEnvironment> {
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

    let test_number = shared.test_counter.fetch_add(1, Ordering::SeqCst);
    // create tmp dir
    let timestamp = shared.start_unix_timestamp_nanos;
    let dir_name = format!("{timestamp}_{test_number}");
    let test_dir = env::temp_dir().join(dir_name.to_string());
    create_dir(&test_dir).context(format!("Failed to create temp directory: {test_dir:?}"))?;

    layout_launcher(
        &test_dir,
        &shared.project_root,
        &shared.jbrsdk_root,
        &shared.intellij_app_jar_source,
        &product_info_path,
    )?;

    let launcher_dir = resolve_launcher_dir(&test_dir, layout_kind);
    env::set_current_dir(&launcher_dir)?;

    let launcher_path = launcher_dir.join("xplat-launcher");

    let result = TestEnvironment {
        launcher_path,
        test_root_dir: test_dir
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

    let start_unix_timestamp_nanos = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)?
        .as_nanos();

    let test_counter = AtomicU32::new(0);
    let result = TestEnvironmentShared {
        project_root,
        jbrsdk_root,
        intellij_app_jar_source,
        start_unix_timestamp_nanos,
        test_counter
    };

    Ok(result)
}

pub struct TestEnvironmentShared {
    project_root: PathBuf,
    jbrsdk_root: PathBuf,
    intellij_app_jar_source: PathBuf,
    start_unix_timestamp_nanos: u128,
    test_counter: AtomicU32
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

pub enum LauncherLocation {
    MainBin,
    PluginsBin
}

#[cfg(target_os = "linux")]
pub fn layout_launcher(
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
        let target = &target_dir.join(target_relative);
        fs::create_dir_all(target.parent_or_err()?)?;
        symlink(source, target).context(format!("Failed to create symlink {target:?} pointing to {source:?}"))?;
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

pub fn resolve_launcher_dir(test_dir: &Path, layout_kind: &LauncherLocation) -> PathBuf {
    let root = match cfg!(target_os = "macos") {
        true => test_dir.join("Contents"),
        false => test_dir.to_path_buf()
    };

    match layout_kind {
        LauncherLocation::MainBin => root.join("bin"),
        LauncherLocation::PluginsBin => root.join("plugins/remote-dev-server/bin")
    }
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
    let output_file = test.test_root_dir.join(TEST_OUTPUT_FILE_NAME);
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

pub fn run_launcher_and_get_dump(layout_kind: &LauncherLocation) -> IntellijMainDumpedLaunchParameters {
    let test = prepare_test_env(layout_kind);
    let result = run_launcher_with_default_args(&test, &[]);
    assert!(result.exit_status.success(), "Launcher didn't exit successfully");
    result.dump.expect("Launcher exited successfully, but there is no output")
}

pub fn run_launcher_and_get_dump_with_args(layout_kind: &LauncherLocation, args: &[&str]) -> IntellijMainDumpedLaunchParameters {
    let test = prepare_test_env(layout_kind);
    let result = run_launcher_with_default_args(&test, args);
    assert!(result.exit_status.success(), "Launcher didn't exit successfully");
    result.dump.expect("Launcher exited successfully, but there is no output")
}