// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub mod utils;

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use crate::utils::*;

    #[cfg(target_os = "linux")]
    use {
        std::path::PathBuf,
        std::fs,
        std::fs::File
    };

    #[test]
    fn remote_dev_args_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let args = &[ "remote_remote_dev_arg_test" ];
        let dump = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_dump().with_args(args)).dump();

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.project_dir.to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    #[test]
    fn remote_dev_known_command_without_project_path_test() {
        let remote_dev_command = &["dumpLaunchParameters"];
        let output = run_launcher(LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        assert!(output.contains("dump-launch-parameters"), "output:\n{}", output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_unknown_command_without_project_path_test() {
        let launch_result = run_launcher(LauncherRunSpec::remote_dev().with_args(&["testCommand"]));

        check_output(&launch_result, |output| output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[test]
    fn remote_dev_known_command_with_project_path_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let remote_dev_command = &["run", &test.project_dir.display().to_string()];
        let output = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        let project_dir = format!("{:?}", &test.project_dir);
        assert!(output.contains("remoteDevHost"), "'remoteDevHost' not in output:\n{}", output);
        assert!(output.contains(project_dir.as_str()), "'{project_dir}' not in output:\n{}", output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_known_command_with_project_path_test_2() {
        let launch_result = run_launcher(LauncherRunSpec::remote_dev().with_args(&["run"]));

        check_output(&launch_result, |output| !output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[test]
    fn remote_dev_known_command_with_project_path_and_arguments_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let project_dir = &test.project_dir.to_string_lossy().to_string();

        let remote_dev_command = &["warmup", project_dir];
        let output = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_args(remote_dev_command)).stdout;

        let project_dir_arg = &format!("--project-dir={}", project_dir.replace('\\', "\\\\"));
        assert!(output.contains("warmup"), "output:\n{}", output);
        assert!(output.contains(project_dir_arg), "'{}' is not in the output:\n{}", project_dir_arg, output);
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"), "output:\n{}", output);
    }

    #[test]
    fn remote_dev_new_ui_test1() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let env = HashMap::from([("REMOTE_DEV_NEW_UI_ENABLED", "1")]);
        let remote_dev_command = &["run", &test.project_dir.display().to_string()];
        let launch_result = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_args(remote_dev_command).with_env(&env));

        check_output(&launch_result, |output| !output.contains("Config folder does not exist, considering this the first launch. Will launch with New UI as default"));
    }

    #[test]
    fn remote_dev_new_ui_test_shared_configs() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let remote_dev_command = &["run", &test.project_dir.display().to_string()];
        let launch_result = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_args(remote_dev_command));

        check_output(&launch_result, |output| !output.contains("Config folder does not exist, considering this the first launch. Will launch with New UI as default"));
    }

    #[cfg(target_os = "linux")]
    fn prepare_font_config_dir(dist_root: &PathBuf) {
        let self_contained_root = &dist_root.join("plugins/remote-dev-server/selfcontained");
        let font_config_root = &self_contained_root.join("fontconfig");
        let fonts_dir = &font_config_root.join("fonts");
        fs::create_dir_all(fonts_dir).unwrap();
        File::create(font_config_root.join("fonts.conf")).unwrap();

        let libs_dir = &self_contained_root.join("lib");
        fs::create_dir_all(libs_dir).unwrap();
        File::create(self_contained_root.join("lib-load-order")).unwrap();
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn remote_dev_font_config_added_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        prepare_font_config_dir(&test.dist_root);

        let expected_path_value = format!("{}/jbrd-fontconfig-", std::env::temp_dir().to_string_lossy());
        let env = HashMap::new();
        check_env_variable(&test, &env, "FONTCONFIG_PATH", expected_path_value);
        check_env_variable(&test, &env, "INTELLIJ_ORIGINAL_ENV_FONTCONFIG_PATH", "\n".to_string());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn remote_dev_font_config_preserved_test() {
        let test = prepare_test_env(LauncherLocation::RemoteDev);
        let env = HashMap::from([("FONTCONFIG_PATH", "/some/existing/path")]);
        prepare_font_config_dir(&test.dist_root);
        let expected_path_value = format!("/some/existing/path:{}/jbrd-fontconfig-", std::env::temp_dir().to_string_lossy());
        check_env_variable(&test, &env, "FONTCONFIG_PATH", expected_path_value);
        check_env_variable(&test, &env, "INTELLIJ_ORIGINAL_ENV_FONTCONFIG_PATH", "/some/existing/path".to_string());
    }

    fn check_env_variable(test: &TestEnvironment, env: &HashMap<&str, &str>, variable_name: &str, expected_value: String) {
        let remote_dev_command = &["printEnvVar", variable_name];
        let launch_result = run_launcher_ext(&test, LauncherRunSpec::remote_dev().with_env(env).with_args(remote_dev_command));

        let expected_output = format!("{}={}", variable_name, expected_value);
        check_output(&launch_result, |output| output.contains(&expected_output));
    }

    fn check_output<Check>(run_result: &LauncherRunResult, check: Check) where Check: FnOnce(&String) -> bool{
        assert!(check(&run_result.stdout), "stdout:\n{}\nstderr:\n{}", run_result.stdout, run_result.stderr)
    }
}
