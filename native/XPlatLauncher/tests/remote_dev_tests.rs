// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use rstest::*;
    use crate::utils::*;

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_args_test(#[case]layout_spec: &LayoutSpec) {
        let test = &prepare_test_env(layout_spec);
        let args = &[ "remote_remote_dev_arg_test" ];

        let result = run_remote_dev_with_default_args_and_env(test, args, std::collections::HashMap::from([(" ", "")]));

        let dump = &result.dump.expect("Exit status was successful, but no dump was received");

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.test_root_dir.path().to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }
}