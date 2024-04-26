#[cfg(test)]
mod tests {
    use std::env;

    #[test]
    fn smoke_test() {
        let restarter_file = env::current_exe().unwrap()
            .parent().unwrap().parent().unwrap()
            .join(if cfg!(target_os = "windows") { "restarter.exe" } else { "restarter" });
        if !restarter_file.exists() {
            panic!("Does not exist: {restarter_file:?}");
        }

        let list_cmd = if cfg!(target_os = "windows") { "dir" } else { "ls" };
        let file_path = restarter_file.to_str().unwrap();
        let output = std::process::Command::new(&restarter_file)
            .args(vec!["777777777", "2", list_cmd, file_path])
            .output().unwrap();

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        assert!(output.status.success(), "Failed: {}\n== stdout:\n{}\n== stderr:\n{}", output.status, stdout, stderr);
        assert!(stdout.contains(file_path), "Line '{}' not found in the output:\n{}", file_path, stdout);
    }
}
