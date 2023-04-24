// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use crate::utils::*;

    /// Tests depending on the shared user config directory cannot run concurrently.
    static USER_DIR_LOCK: Mutex<usize> = Mutex::new(0);

    #[test]
    fn selecting_user_config_runtime_test() {
        let _lock = USER_DIR_LOCK.lock().expect("Failed to acquire the user directory lock");
        let mut test = prepare_test_env(LauncherLocation::Standard);

        let expected_rt = test.create_jbr_link("_user_jbr");
        let jdk_config_name = if cfg!(target_os = "windows") { "xplat64.exe.jdk" } else { "xplat.jdk" };
        test.create_user_config_file(jdk_config_name, expected_rt.to_str().unwrap());

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn user_vm_options_loading_test() {
        let _lock = USER_DIR_LOCK.lock().expect("Failed to acquire the user directory lock");
        let mut test = prepare_test_env(LauncherLocation::Standard);

        let vm_options_name = if cfg!(target_os = "windows") { "xplat64.exe.vmoptions" }
            else if cfg!(target_os = "macos") { "xplat.vmoptions" }
            else { "xplat64.vmoptions" };
        let vm_options_file = test.create_user_config_file(vm_options_name, "-Done.user.option=whatever\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, &format!("-Djb.vmOptionsFile={}", vm_options_file.to_str().unwrap()));
    }
}
