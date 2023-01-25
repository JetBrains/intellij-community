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

    // todo: write run_RD realisation with custom command                X
    // todo: redirect std_out to file and load it                        X
    // todo: 1) существующая команда с проектом                          X
    // todo: 2) существующая команда без проекта                         X
    // todo: 3) существающая команда с двумя ключами (warm-up и warmup)  X
    // todo: 4) несуществующая команда                                   X

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_known_command_without_project_path_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let remote_dev_command = &["dumpLaunchParameters"];
        let dump = run_remote_dev_and_get_output_with_args(&test, remote_dev_command);

        println!("-----------{}", dump)
    }
}