use crate::tests_util::{get_child_dir, gradle_command_wrapper, layout_into_test_dir};
use std::env;
use std::env::temp_dir;
use std::fs::create_dir;
use std::path::{Path};
use std::sync::Once;
use std::time::SystemTime;

mod tests_util;

static INIT: Once = Once::new();

// @BeforeAll
pub fn initialize() {
    INIT.call_once(|| {
        let project_root = env::current_dir().expect("Failed to get project root");

        gradle_command_wrapper("clean");
        gradle_command_wrapper("jar");

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
        let jbrsdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix).expect("");

        // jbrsdk-17.0.3-x64-b469
        let jbrsdk_root = get_child_dir(&jbrsdk_gradle_parent, java_dir_prefix).expect("");

        let os = env::consts::OS;
        let kek = format!("resources/product_info_{os}.json");
        let product_info_path = Path::new(&kek);
        let jar_path = Path::new("resources/TestProject/build/libs/app.jar");
        let jar_absolute_path = env::current_dir().unwrap().join(jar_path);
        let product_info_absolute_path = env::current_dir().unwrap().join(product_info_path);

        // create tmp dir
        let dir_name = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .expect("Failed to compute current unix timestamp")
            .as_secs();
        let test_dir_path = temp_dir().join(dir_name.to_string());
        create_dir(&test_dir_path).expect("Failed to create temp dir");

        env::set_current_dir(test_dir_path).expect("Failed to set current dir");

        layout_into_test_dir(
            &project_root,
            jbrsdk_root,
            jar_absolute_path,
            product_info_absolute_path,
        );
    });
}

#[cfg(test)]
// | What do we need to check | status |
// |--------------------------|:------:|
// | launcher exit status     |   V    |
// | Class path               |   V    |
// | vm options               |   X    |  TODO: add tests after dehardcode
// | command line arguments   |   V    |
// | current Java version     |   V    |
// | current Java vendor      |   V    |
// | work dir                 |   V    |
// | PATH, LD_LIBRARY_PATH... |   X    |  writeEnvironmentVariableInFile(String envVariable)
// | setup JRE                |   X    |

mod tests {
    use crate::initialize;
    use crate::tests_util::resolve_test_dir;
    use std::path::PathBuf;
    use std::process::{Command, ExitStatus};
    use std::time::Duration;
    use std::{fs, thread, time};

    fn start_launcher(test_dir: PathBuf) -> ExitStatus {
        let mut launcher_process = Command::new(test_dir.join("xplat_launcher")) // for windows xplat_launcher.exe???
            .current_dir(test_dir)
            .args(["test_argument1", "test_argument2"])
            .env(xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1")
            .spawn()
            .expect("Failed to spawn launcher process");

        let started = time::Instant::now();

        loop {
            let elapsed = time::Instant::now() - started;
            if elapsed > Duration::from_secs(60) {
                panic!("Launcher has been running for more than 60 seconds, terminating")
            }

            match launcher_process.try_wait() {
                Ok(opt) => match opt {
                    None => {
                        println!("Waiting for launcher process to exit");
                    }
                    Some(es) => return es,
                },
                Err(e) => {
                    panic!("Failed to get launcher process status: {e:?}")
                }
            };

            thread::sleep(Duration::from_secs(1))
        }
    }

    fn read_file(filename: &str) -> String {
        let file_to_open = resolve_test_dir().join(filename);
        return fs::read_to_string(file_to_open).expect(format!("Can't read {filename}").as_str());
    }

    #[test]
    fn correct_launcher_startup_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );
    }

    #[test]
    fn classpath_test() {
        // a dummy classpath parameter was added to product_info.json for this test
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let classpath = read_file("java.class.path.txt");

        let separator = std::path::MAIN_SEPARATOR;
        assert!(
            classpath.contains(format!("lib{separator}app.jar").as_str()),
            "Classpath does not contain app.jar"
        );
        assert!(
            classpath.contains(format!("lib{separator}test.jar").as_str()),
            "Dummy classpath wasn't read from product_info.json"
        );
    }

    #[test]
    fn vm_options_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let _vm_options = read_file("vm.options.txt");
        // TODO asserts
    }

    #[test]
    fn arguments_test() {
        // a dummy  arguments was added to launcher for this test
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let launcher_dir = resolve_test_dir()
            .join("xplat_launcher")
            .into_os_string()
            .into_string()
            .unwrap();
        let arguments = read_file("arguments.txt");
        let mut arguments_lines = arguments.lines();

        assert_eq!(launcher_dir, arguments_lines.next().unwrap());
        assert_eq!(
            Some("test_argument1"),
            arguments_lines.next(),
            "Incorrect dummy arguments"
        );
        assert_eq!(
            Some("test_argument2"),
            arguments_lines.next(),
            "Incorrect dummy arguments"
        );
        assert_eq!(
            0,
            arguments_lines.count(),
            "Extra command line arguments detected"
        );
    }

    #[test]
    fn java_home_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let java_home = read_file("java.home.txt");
        assert!(!java_home.is_empty(), "Java Home is empty");
    }

    #[test]
    fn java_version_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let java_version = read_file("java.version.txt");
        assert!(
            java_version.starts_with("17"),
            "Incorrect Java version. Expected 17"
        );
    }

    #[test]
    fn java_vendor_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let java_version = read_file("java.vendor.txt");
        assert!(
            java_version.starts_with("JetBrains"),
            "Incorrect Java vendor"
        );
    }

    #[test]
    fn work_dir_test() {
        initialize();
        let test_dir = resolve_test_dir();

        let launcher_exit_status = start_launcher(test_dir);
        assert!(
            launcher_exit_status.success(),
            "The exit code of the launcher is not successful"
        );

        let user_dir = read_file("user.dir.txt");
        assert_eq!(
            resolve_test_dir().into_os_string().into_string().unwrap(),
            user_dir,
            "Incorrect launcher work dir"
        );
    }
}
