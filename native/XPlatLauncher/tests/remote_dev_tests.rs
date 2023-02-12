// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use rstest::*;
    use crate::utils::*;

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_args_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let args = &[ "remote_remote_dev_arg_test" ];

        let dump = run_remote_dev_and_get_dump_with_args(&test, args);

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.test_root_dir.path().to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_known_command_without_project_path_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let remote_dev_command = &["dumpLaunchParameters"];
        let output = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        assert!(output.contains("dump-launch-parameters"));
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_unknown_command_without_project_path_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let remote_dev_command = &["testCommand"];
        let output = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        assert!(output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_known_command_with_project_path_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let project_dir = &test.test_root_dir.path().to_string_lossy().to_string();

        let remote_dev_command = &["run", &project_dir];
        let output = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        assert!(output.contains("cwmHostNoLobby"));
        assert!(output.contains(project_dir));
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_known_command_with_project_path_test_1(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let remote_dev_command = &["run"];
        let output = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        assert!(output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_known_command_with_project_path_and_arguments_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let project_dir = &test.test_root_dir.path().to_string_lossy().to_string();

        let remote_dev_command = &["warmup", project_dir];
        let output = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        assert!(output.contains("warmup"));
        assert!(output.contains(&format!("--project-dir={}", project_dir)));
        assert!(!output.contains("Usage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]"));
    }
}