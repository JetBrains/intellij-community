mod tests_util;

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
    use crate::tests_util::{initialize, resolve_test_dir};
    use std::path::PathBuf;
    use std::process::{Command, ExitStatus};
    use std::time::Duration;
    use std::{fs, thread, time};

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
            .join("xplat-launcher")
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



    // TODO: test for additionalJvmArguments in product-info.json being set
    // (e.g. "-Didea.vendor.name=JetBrains")

    fn start_launcher(test_dir: PathBuf) -> ExitStatus {
        let mut launcher_process = Command::new(test_dir.join("xplat-launcher")) // for windows xplat-launcher.exe???
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
}
