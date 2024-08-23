// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub mod utils;

#[cfg(test)]
mod tests {
    use std::path::PathBuf;
    use std::sync::Mutex;
    use xplat_launcher::{get_config_home, jvm_property};
    use crate::utils::*;

    /// Tests depending on the shared user config directory cannot run concurrently.
    static USER_DIR_LOCK: Mutex<usize> = Mutex::new(0);

    #[test]
    fn selecting_user_config_runtime_test() {
        let _lock = USER_DIR_LOCK.lock().expect("Failed to acquire the user directory lock");
        let mut test = prepare_test_env(LauncherLocation::Standard);

        let expected_rt = test.create_jbr_link("_user_jbr");
        let jdk_config_name = if cfg!(target_os = "windows") { "xplat64.exe.jdk" } else { "xplat.jdk" };
        test.create_user_config_file(jdk_config_name, expected_rt.to_str().unwrap(), get_standard_config_dir());

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn user_vm_options_loading_test() {
        let _lock = USER_DIR_LOCK.lock().expect("Failed to acquire the user directory lock");
        let mut test = prepare_test_env(LauncherLocation::Standard);

        let vm_options_name = get_vm_options_name();
        let vm_options_file = test.create_user_config_file(vm_options_name, "-Done.user.option=whatever\n", get_standard_config_dir());

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, &jvm_property!("jb.vmOptionsFile", vm_options_file.to_str().unwrap()));
    }

    fn get_vm_options_name() -> &'static str {
        if cfg!(target_os = "windows") { "xplat64.exe.vmoptions" } else if cfg!(target_os = "macos") { "xplat.vmoptions" } else { "xplat64.vmoptions" }
    }

    #[test]
    fn user_vm_options_loading_for_custom_command_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);

        let vm_options_name = get_vm_options_name();
        test.create_user_config_file(vm_options_name, "-Dcustom.property=custom.value\n", get_custom_config_dir());

        let result = run_launcher(LauncherRunSpec::standard().with_args(&["custom-command"]).assert_status());
        assert!(result.stdout.contains("Custom command: product.property=product.value, custom.property=custom.value"), "Custom system property is not set: {:?}", result);
    }

    fn get_standard_config_dir() -> PathBuf {
        get_config_home().unwrap().join("JetBrains").join("XPlatLauncherTest")
    }

    fn get_custom_config_dir() -> PathBuf {
        get_config_home().unwrap().join("JetBrains").join("XPlatLauncherTestCustom")
    }
}
