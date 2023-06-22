// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub mod utils;

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::env;
    use std::path::Path;
    use xplat_launcher::get_config_home;
    use crate::utils::*;

    #[test]
    fn remote_dev_args_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let args = &[ "remote_remote_dev_arg_test" ];
        let dump = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_dump().with_args(args)).dump();

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.project_dir.to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    #[test]
    fn remote_dev_known_command_without_project_path_test() {
        let remote_dev_command = &["dumpLaunchParameters"];
        let output = run_launcher(&LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        assert!(output.contains("dump-launch-parameters"), "output:\n{}", output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_unknown_command_without_project_path_test() {
        let output = run_launcher(&LauncherRunSpec::remote_dev().with_args(&["testCommand"])).stdout;

        assert!(output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_known_command_with_project_path_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        let remote_dev_command = &["run", &project_dir];
        let output = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        assert!(output.contains("cwmHostNoLobby"), "output:\n{}", output);
        assert!(output.contains(project_dir), "output:\n{}", output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_known_command_with_project_path_test_1() {
        let output = run_launcher(&LauncherRunSpec::remote_dev().with_args(&["run"])).stdout;

        assert!(output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[test]
    fn remote_dev_known_command_with_project_path_and_arguments_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        let remote_dev_command = &["warmup", project_dir];
        let output = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        let project_dir_arg = &format!("--project-dir={}", project_dir.replace('\\', "\\\\"));
        assert!(output.contains("warmup"), "output:\n{}", output);
        assert!(output.contains(project_dir_arg), "'{}' is not in the output:\n{}", project_dir_arg, output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_new_ui_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        // When starting the Launcher, we set this variable always with the value projectDir. For the test, we overwrite it with a non-existent directory
        let fake_config_dir_path: &Path = &test.project_dir.join("fakeDir");

        let env = HashMap::from([("IJ_HOST_CONFIG_DIR", fake_config_dir_path.to_str().unwrap())]);
        let remote_dev_command = &["run", &project_dir];
        let output = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_args(remote_dev_command).with_env(&env)).stdout;

        assert!(output.contains("Config folder does not exist, considering this the first launch. Will launch with New UI as default"));
        assert!(output.contains("Force enable new UI"));
        assert!(output.contains("early-access-registry.txt"));
    }

    #[test]
    fn remote_dev_new_ui_test1() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        let env = HashMap::from([("REMOTE_DEV_NEW_UI_ENABLED", "1")]);
        let remote_dev_command = &["run", &project_dir];
        let output = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_args(remote_dev_command).with_env(&env)).stdout;

        assert!(!output.contains("Config folder does not exist, considering this the first launch. Will launch with New UI as default"));
        assert!(output.contains("Force enable new UI"));
        assert!(output.contains("early-access-registry.txt"));
    }

    #[test]
    fn remote_dev_new_ui_test2() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        let remote_dev_command = &["run", &project_dir];
        let output = run_launcher_ext(&test, &LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        assert!(!output.contains("Force enable new UI"));
    }
}
