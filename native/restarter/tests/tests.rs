// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#[cfg(test)]
mod tests {
    use std::env;

    #[test]
    fn smoke_test() {
        let test_file = env::current_exe().unwrap();
        let bin_dir = test_file.parent().unwrap().parent().unwrap();
        let restarter_file = bin_dir.join(if cfg!(target_os = "windows") { "restarter.exe" } else { "restarter" });
        if !restarter_file.exists() {
            panic!("Does not exist: {restarter_file:?}");
        }

        let file_path = restarter_file.to_str().unwrap();
        let output = std::process::Command::new(&restarter_file)
            .args(if cfg!(target_os = "windows") {
                vec!["777777777", "4", "cmd", "/c", "dir", file_path]
            } else {
                vec!["777777777", "2", "ls", file_path]
            })
            .output().unwrap();

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        let marker = if cfg!(target_os = "windows") { bin_dir.to_str().unwrap() } else { file_path };
        assert!(output.status.success(), "Failed: {}\n== stdout:\n{}\n== stderr:\n{}", output.status, stdout, stderr);
        assert!(stdout.contains(marker), "Line '{}' not found in the output:\n{}", marker, stdout);
    }
}
