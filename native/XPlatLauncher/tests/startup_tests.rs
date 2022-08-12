use crate::tests_util::{get_child_dir, gradle_command_wrapper, layout_into_test_dir};
use std::env;
use std::env::temp_dir;
use std::fs::create_dir;
use std::path::Path;
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

        // jbrsdk-17.0.3-osx-x64-b469.37-f87880
        let jbrsdk_gradle_parent = get_child_dir(&gradle_jvm, "jbrsdk").expect("");

        // jbrsdk-17.0.3-x64-b469
        let jbrsdk_root = get_child_dir(&jbrsdk_gradle_parent, "jbrsdk").expect("");

        let product_info_path = Path::new("resources/product_info.json");
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
mod tests {
    use crate::initialize;
    use std::env::current_dir;
    use std::process::Command;
    use std::time::Duration;
    use std::{thread, time};

    #[test]
    fn test_test() {
        initialize();
        let test_dir = if cfg!(target_os = "macos") {
            current_dir().unwrap().join("Contents").join("bin")
        } else {
            current_dir().unwrap().join("bin")
        };

        let mut launcher_process = Command::new(test_dir.join("xplat_launcher"))
            .current_dir(test_dir)
            .spawn()
            .expect("Failed to spawn launcher process");

        let started = time::Instant::now();

        loop {
            let elapsed = time::Instant::now() - started;
            if elapsed > Duration::from_secs(60) {
                panic!("Launcher has been running for more than 60 seconds, terminating")
            }

            match launcher_process.try_wait() {
                Ok(opt) => {
                    match opt {
                        None => {
                            println!("Waiting for launcher process to exit");
                        }
                        Some(es) => match es.code() {
                            None => {
                                panic!("No exit code for launcher process, probably terminated by signal")
                            }
                            Some(c) => {
                                match c {
                                    0 => {
                                        println!("Launcher exited with exit code 0")
                                    }
                                    x => {
                                        panic!("Launcher exited with non-zero exit code {x}");
                                    }
                                }

                                break;
                            }
                        },
                    }
                }
                Err(e) => {
                    panic!("Failed to get launcher process status: {e:?}")
                }
            };

            thread::sleep(Duration::from_secs(1))
        }

        assert_eq!(2, 2);
    }
}
