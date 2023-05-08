// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::{env, fs, thread, time};
use std::collections::HashMap;
use std::fmt::{Debug, Formatter};
use std::fs::{File, OpenOptions};
use std::io::{BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Stdio};
use std::sync::Once;

use anyhow::{bail, Context, Result};
use log::debug;
use serde::{Deserialize, Serialize};
use tempfile::{Builder, TempDir};

use xplat_launcher::{DEBUG_MODE_ENV_VAR, get_config_home, PathExt};

static INIT: Once = Once::new();
static mut SHARED: Option<TestEnvironmentShared> = None;

#[derive(Clone)]
pub enum LauncherLocation { Standard, RemoteDev }

pub struct TestEnvironment<'a> {
    pub dist_root: PathBuf,
    pub project_dir: PathBuf,
    launcher_path: PathBuf,
    shared_env: &'a TestEnvironmentShared,
    test_root_dir: TempDir,
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
        fs::create_dir_all(&temp_dir).expect(&format!("Cannot create: {:?}", temp_dir));
        temp_dir
    }

    pub fn create_temp_file(&self, relative_path: &str, content: &str) -> PathBuf {
        let temp_file = self.test_root_dir.path().join(relative_path);
        Self::create_file(&temp_file, content);
        temp_file
    }

    pub fn create_user_config_file(&mut self, name: &str, content: &str) -> PathBuf {
        let custom_config_dir = get_custom_config_dir();
        self.to_delete.push(custom_config_dir.clone());
        let config_file = custom_config_dir.join(name);
        Self::create_file(&config_file, content);
        config_file
    }

    pub fn create_toolbox_vm_options(&mut self, content: &str) -> PathBuf {
        let dist_root = if cfg!(target_os = "macos") { self.dist_root.parent().unwrap() } else { &self.dist_root };
        let vm_options_name = dist_root.file_name().unwrap().to_str().unwrap().to_string() + ".vmoptions";
        let vm_options_file = dist_root.parent().unwrap().join(vm_options_name);
        self.to_delete.push(vm_options_file.clone());
        Self::create_file(&vm_options_file, content);
        vm_options_file
    }

    fn create_file(file: &Path, content: &str) {
        fs::create_dir_all(file.parent().unwrap())
            .expect(&format!("Cannot create: {:?}", file));
        OpenOptions::new().write(true).create_new(true).open(&file)
            .expect(&format!("Cannot create {:?}", &file))
            .write_all(content.as_bytes())
            .expect(&format!("Cannot write {:?}", &file));
    }

    #[cfg(target_os = "windows")]
    pub fn to_unc(&self) -> Self {
        self.map_path(Self::convert_to_unc)
    }

    #[cfg(target_os = "windows")]
    pub fn to_ns_prefix(&self) -> Self {
        self.map_path(Self::ns_prefix)
    }

    #[cfg(target_os = "windows")]
    fn map_path(&self, mapping: fn(&Path) -> PathBuf) -> Self {
        let new_temp_dir = mapping(self.test_root_dir.path().parent().unwrap());
        TestEnvironment {
            dist_root: mapping(&self.dist_root),
            project_dir: mapping(&self.project_dir),
            launcher_path: mapping(&self.launcher_path),
            shared_env: self.shared_env,
            test_root_dir: Builder::new().prefix("xplat_launcher_test_").tempdir_in(new_temp_dir).unwrap(),
            to_delete: Vec::new()
        }
    }

    // "C:\some\path" -> "\\127.0.0.1\\C$\some\path"
    #[cfg(target_os = "windows")]
    fn convert_to_unc(path: &Path) -> PathBuf {
        assert!(path.has_root(), "Invalid path: {:?}", path);
        let path_str = path.to_str().unwrap();
        PathBuf::from(String::from("\\\\127.0.0.1\\") + &path_str[0..1] + "$" + &path_str[2..])
    }

    // "C:\some\path" -> "\\?\C:\some\path"
    #[cfg(target_os = "windows")]
    fn ns_prefix(path: &Path) -> PathBuf {
        assert!(path.has_root(), "Invalid path: {:?}", path);
        PathBuf::from(String::from("\\\\?\\") + path.to_str().unwrap())
    }
}

impl<'a> Drop for TestEnvironment<'a> {
    fn drop(&mut self) {
        for path in &self.to_delete {
            debug!("Deleting {:?}", path);
            if let Ok(metadata) = path.symlink_metadata() {
                let result = if metadata.is_dir() { fs::remove_dir_all(&path) } else { fs::remove_file(path) };
                result.expect(&format!("cannot delete: {:?}", path))
            }
        }
    }
}

pub fn prepare_test_env<'a>(launcher_location: LauncherLocation) -> TestEnvironment<'a> {
    match prepare_test_env_impl(launcher_location, true) {
        Ok(x) => x,
        Err(e) => panic!("Failed to prepare test environment: {:?}", e),
    }
}

pub fn prepare_no_jbr_test_env<'a>(launcher_location: LauncherLocation) -> TestEnvironment<'a> {
    match prepare_test_env_impl(launcher_location, false) {
        Ok(x) => x,
        Err(e) => panic!("Failed to prepare test environment: {:?}", e),
    }
}

struct TestEnvironmentShared {
    launcher_path: PathBuf,
    jbr_root: PathBuf,
    app_jar_path: PathBuf,
    product_info_path: PathBuf,
    vm_options_path: PathBuf
}

fn prepare_test_env_impl<'a>(launcher_location: LauncherLocation, with_jbr: bool) -> Result<TestEnvironment<'a>> {
    INIT.call_once(|| {
        let shared = init_test_environment_once().expect("Failed to init shared test environment");
        unsafe {
            SHARED = Some(shared)
        }
    });

    let shared_env = unsafe { SHARED.as_ref() }.expect("Shared test environment should have already been initialized");

    let temp_dir = Builder::new().prefix("xplat_launcher_test_").tempdir().context("Failed to create temp directory")?;

    let (dist_root, launcher_path) = layout_launcher(launcher_location, with_jbr, temp_dir.path(), shared_env)?;

    let project_dir = temp_dir.path().join("_project");
    fs::create_dir_all(&project_dir)?;

    Ok(TestEnvironment { dist_root, project_dir, launcher_path, shared_env, test_root_dir: temp_dir, to_delete: Vec::new() })
}

fn init_test_environment_once() -> Result<TestEnvironmentShared> {
    //xplat_launcher::mini_logger::init(log::LevelFilter::Debug)?;

    // clean environment variables
    env::remove_var("JDK_HOME");
    env::remove_var("JAVA_HOME");

    let project_root = env::current_dir().expect("Failed to get project root");

    let build_target = if cfg!(debug_assertions) { "debug" } else { "release" };
    let bin_name = if cfg!(target_os = "windows") { "xplat-launcher.exe" } else { "xplat-launcher" };
    let launcher_path = project_root.join("target").join(build_target).join(bin_name);
    if !launcher_path.exists() {
        bail!("Didn't find source launcher to layout, expected path: {:?}", launcher_path);
    }

    gradle_command_wrapper("fatJar")?;

    let jbr_root = get_jbr_sdk_from_project_root(&project_root)?;

    let app_jar_path = Path::new("./resources/TestProject/build/libs/app.jar").canonicalize()?;

    let product_info_path = project_root.join(format!("resources/product_info_{}.json", env::consts::OS));
    let vm_options_path = project_root.join("resources/xplat.vmoptions");

    Ok(TestEnvironmentShared { launcher_path, jbr_root, app_jar_path, product_info_path, vm_options_path })
}

fn get_jbr_sdk_from_project_root(project_root: &Path) -> Result<PathBuf> {
    let gradle_jvm = project_root.join("resources").join("TestProject").join("gradle-jvm");

    // TODO: remove after wrapper with https://github.com/mfilippov/gradle-jvm-wrapper/pull/31
    let java_dir_prefix = if cfg!(target_os = "windows") { "jdk" } else { "jbrsdk" };

    // jbrsdk-17.0.3-osx-x64-b469.37-f87880
    let sdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix)?;

    // jbrsdk-17.0.3-x64-b469
    let sdk_root = get_child_dir(&sdk_gradle_parent, java_dir_prefix)?;

    Ok(sdk_root)
}

fn gradle_command_wrapper(gradle_task: &str) -> Result<()> {
    debug!("current_dir={:?}", env::current_dir());

    let wrapper_name = if cfg!(target_os = "windows") { "gradlew.bat" } else { "gradlew" };
    let wrapper_path = PathBuf::from("./resources/TestProject").join(wrapper_name).canonicalize()?;
    if !wrapper_path.is_executable()? {
        bail!("Not an executable file: {:?}", wrapper_path);
    }

    debug!("Running '{} :{}'", wrapper_path.display(), gradle_task);
    let output = Command::new(wrapper_path)
        .arg(gradle_task)
        .current_dir("./resources/TestProject")
        .output()
        .with_context(|| format!("Failed to execute 'gradlew :{gradle_task}'"))?;

    let exit_status = output.status;
    if !exit_status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        bail!("Command didn't succeed: exit code: {exit_status}\nstdout: <<<{stdout}>>>,\nstderr: <<<{stderr}>>>")
    }

    Ok(())
}

fn get_child_dir(parent: &Path, prefix: &str) -> Result<PathBuf> {
    let entry = fs::read_dir(parent)?
        .map(|r| r.unwrap())
        .find(|e| e.file_name().to_string_lossy().starts_with(prefix));
    match entry {
        Some(e) => Ok(e.path()),
        None => bail!("'{}*' not found in {:?}", prefix, parent)
    }
}

#[cfg(target_os = "linux")]
fn layout_launcher(
    launcher_location: LauncherLocation,
    include_jbr: bool,
    target_dir: &Path,
    shared_env: &TestEnvironmentShared
) -> Result<(PathBuf, PathBuf)> {
    // .
    // ├── bin/
    // │   └── xplat-launcher | remote-dev-server
    // │   └── xplat64.vmoptions
    // │   └── idea.properties
    // ├── lib/
    // │   └── app.jar
    // │   └── boot-linux.jar
    // ├── jbr/
    // └── product-info.json

    let launcher_rel_path = match launcher_location {
        LauncherLocation::Standard => "bin/xplat-launcher",
        LauncherLocation::RemoteDev => "bin/remote-dev-server"
    };

    layout_launcher_impl(
        target_dir,
        vec![
            "bin/idea.properties",
            "lib/boot-linux.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.vm_options_path, "bin/xplat64.vmoptions"),
            (&shared_env.app_jar_path, "lib/app.jar"),
            (&shared_env.product_info_path, "product-info.json")
        ],
        include_jbr,
        &shared_env.jbr_root
    )?;

    Ok((target_dir.to_path_buf(), target_dir.join(launcher_rel_path)))
}

#[cfg(target_os = "macos")]
fn layout_launcher(
    launcher_location: LauncherLocation,
    include_jbr: bool,
    target_dir: &Path,
    shared_env: &TestEnvironmentShared
) -> Result<(PathBuf, PathBuf)> {
    // .
    // └── Contents
    //     ├── bin/
    //     │   └── xplat-launcher | remote-dev-server
    //     │   └── xplat.vmoptions
    //     │   └── idea.properties
    //     ├── Resources/
    //     │   └── product-info.json
    //     ├── lib/
    //     │   └── app.jar
    //     │   └── boot-macos.jar
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
            "lib/boot-macos.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.vm_options_path, "bin/xplat.vmoptions"),
            (&shared_env.app_jar_path, "lib/app.jar"),
            (&shared_env.product_info_path, "Resources/product-info.json")
        ],
        include_jbr,
        &shared_env.jbr_root
    )?;

    let launcher_path = dist_root.join(launcher_rel_path);
    Ok((dist_root, launcher_path))
}

#[cfg(target_os = "windows")]
fn layout_launcher(
    launcher_location: LauncherLocation,
    include_jbr: bool,
    target_dir: &Path,
    shared_env: &TestEnvironmentShared
) -> Result<(PathBuf, PathBuf)> {
    // .
    // ├── bin/
    // │   └── xplat-launcher.exe | remote-dev-server.exe
    // │   └── xplat64.exe.vmoptions
    // │   └── idea.properties
    // ├── lib/
    // │   └── app.jar
    // │   └── boot-windows.jar
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
            "lib\\boot-windows.jar"
        ],
        vec![
            (&shared_env.launcher_path, launcher_rel_path),
            (&shared_env.vm_options_path, "bin\\xplat64.exe.vmoptions"),
            (&shared_env.app_jar_path, "lib\\app.jar"),
            (&shared_env.product_info_path, "product-info.json")
        ],
        include_jbr,
        &shared_env.jbr_root
    )?;

    Ok((target_dir.to_path_buf(), target_dir.join(launcher_rel_path)))
}

fn layout_launcher_impl(
    target_dir: &Path,
    create_files: Vec<&str>,
    copy_files: Vec<(&Path, &str)>,
    include_jbr: bool,
    jbr_path: &Path
) -> Result<()> {
    for target_rel_path in create_files {
        let target = &target_dir.join(target_rel_path);
        fs::create_dir_all(target.parent_or_err()?)?;
        File::create(target).with_context(|| format!("Failed to create file {target:?}"))?;
    }

    for (source, target_rel_path) in copy_files {
        let target = &target_dir.join(target_rel_path);
        fs::create_dir_all(target.parent_or_err()?)?;
        fs::copy(source, target).with_context(|| format!("Failed to copy from {source:?} to {target:?}"))?;
    }

    if include_jbr {
        symlink(jbr_path, &target_dir.join("jbr"))?;
    }

    Ok(())
}

#[cfg(target_family = "unix")]
fn symlink(original: &Path, link: &Path) -> Result<()> {
    std::os::unix::fs::symlink(original, link)
        .with_context(|| format!("Failed to create symlink {link:?} pointing to {original:?}"))?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn symlink(original: &Path, link: &Path) -> Result<()> {
    std::os::windows::fs::symlink_dir(original, link)
        .with_context(|| format!("Failed to create symlink {link:?} pointing to {original:?}"))?;

    Ok(())
}

#[cfg(target_os = "linux")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("bin/java")
}

#[cfg(target_os = "windows")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("bin\\java.exe")
}

#[cfg(target_os = "macos")]
pub fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("Contents/Home/bin/java")
}

fn get_custom_config_dir() -> PathBuf {
    get_config_home().unwrap().join("JetBrains").join("XPlatLauncherTest")
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
            Some(result) => result.expect(&err_message),
            None => panic!("Dump was not requested; add `.with_dump()` to the run specification")
        }
    }
}

impl Debug for LauncherRunResult {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        f.write_fmt(format_args!(
            "\n** exit code: {:?} ({:?})\n** stderr: <<<{}>>>\n** stdout: <<<{}>>>",
            self.exit_status.code(), self.exit_status, self.stderr, self.stdout))
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
    debug!("Starting '{}'\n  with args {:?}\n  in '{}'",
           test_env.launcher_path.display(), run_spec.args, test_env.test_root_dir.path().display());

    let stdout_file_path = test_env.test_root_dir.path().join("out.txt");
    let stderr_file_path = test_env.test_root_dir.path().join("err.txt");
    let project_dir = test_env.project_dir.to_str().unwrap();
    let dump_file_path = test_env.test_root_dir.path().join("output.json");
    let dump_file_path_str = dump_file_path.strip_ns_prefix()?.to_string_checked()?;

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
            (DEBUG_MODE_ENV_VAR, "1")
        ]),
        LauncherLocation::RemoteDev => HashMap::from([
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
        .current_dir(&test_env.test_root_dir.path())
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

        if let Some(es) = launcher_process.try_wait()? {
            return Ok(LauncherRunResult {
                exit_status: es,
                stdout: fs::read_to_string(&stdout_file_path).context("Cannot open stdout file")?,
                stderr: fs::read_to_string(&stderr_file_path).context("Cannot open stderr file")?,
                dump: if run_spec.dump { Some(read_launcher_run_result(&dump_file_path)) } else { None }
            });
        }

        thread::sleep(time::Duration::from_secs(1))
    }
}

fn read_launcher_run_result(path: &Path) -> Result<IntellijMainDumpedLaunchParameters> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut text = String::new();
    reader.read_to_string(&mut text)?;
    let dump: IntellijMainDumpedLaunchParameters = serde_json::from_str(&text)?;
    Ok(dump)
}

pub fn test_runtime_selection(result: LauncherRunResult, expected_rt: PathBuf) {
    let rt_line = result.stdout.lines().into_iter()
        .find(|line| line.contains("Resolved runtime: "))
        .expect(&format!("The 'Resolved runtime:' line is not in the output: {}", result.stdout));
    let resolved_rt = rt_line.split_once("Resolved runtime: ").unwrap().1;
    let actual_rt = &resolved_rt[1..resolved_rt.len() - 1].replace("\\\\", "\\");

    let adjusted_rt = if cfg!(target_os = "macos") { expected_rt.join("Contents/Home") } else { expected_rt };

    assert_eq!(adjusted_rt.to_str().unwrap(), actual_rt, "Wrong runtime; run result: {:?}", result);
}

pub fn assert_vm_option_presence(dump: &IntellijMainDumpedLaunchParameters, vm_option: &str) {
    assert!(dump.vmOptions.contains(&vm_option.to_string()),
            "{:?} is not in {:?}", vm_option, dump.vmOptions);
}

pub fn assert_vm_option_absence(dump: &IntellijMainDumpedLaunchParameters, vm_option: &str) {
    assert!(!dump.vmOptions.contains(&vm_option.to_string()),
            "{:?} should not be in {:?}", vm_option, dump.vmOptions);
}
