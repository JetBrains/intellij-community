// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::fs;
use std::io::ErrorKind;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

const OVERLAY_RELATIVE_PATH: &str = "tbe/managed-backend.vmoptions";
const MAX_OPTION_COUNT: usize = 10_000;
const MAX_OPTION_LENGTH: usize = 8 * 1024;
const MAX_OVERLAY_SIZE: u64 = 1024 * 1024;

pub(crate) fn load_for_launch(
    vm_options: &[String],
    default_config_dir: &Path,
    ide_home: &Path,
) -> Result<Vec<String>> {
    let config_dir = resolve_config_dir(vm_options, default_config_dir, ide_home)?;
    let overlay_path = config_dir.join(OVERLAY_RELATIVE_PATH);
    match fs::symlink_metadata(&overlay_path) {
        Ok(_) => {}
        Err(error) if error.kind() == ErrorKind::NotFound => {
            return Ok(Vec::new());
        }
        Err(error) => {
            return Err(error).context("Cannot inspect managed backend VM options overlay");
        }
    }

    read_overlay(&config_dir, &overlay_path)
}

fn resolve_config_dir(options: &[String], default_config_dir: &Path, ide_home: &Path) -> Result<PathBuf> {
    if let Some(configured) = options
        .iter()
        .rev()
        .find_map(|option| option.strip_prefix("-Didea.config.path="))
    {
        return absolute_idea_path(configured);
    }

    let mut property_files = Vec::new();
    if let Some(custom_file) = options
        .iter()
        .rev()
        .find_map(|option| option.strip_prefix("-Didea.properties.file="))
    {
        property_files.push(absolute_idea_path(custom_file)?);
    }
    property_files.push(default_config_dir.join("idea.properties"));
    property_files.push(crate::get_user_home()?.join("idea.properties"));
    property_files.push(ide_home.join("bin/idea.properties"));

    for property_file in property_files {
        if let Some(configured) = read_java_property(&property_file, "idea.config.path") {
            return absolute_idea_path(&substitute_path_variables(&configured, ide_home)?);
        }
    }
    Ok(default_config_dir.to_path_buf())
}

fn absolute_idea_path(value: &str) -> Result<PathBuf> {
    let unquoted = value
        .strip_prefix('"')
        .and_then(|value| value.strip_suffix('"'))
        .unwrap_or(value);
    let expanded = if unquoted.starts_with("~/") || unquoted.starts_with("~\\") {
        crate::get_user_home()?.join(&unquoted[2..])
    }
    else {
        PathBuf::from(unquoted)
    };
    if expanded.is_absolute() {
        Ok(expanded)
    }
    else {
        Ok(env::current_dir()?.join(expanded))
    }
}

fn substitute_path_variables(value: &str, ide_home: &Path) -> Result<String> {
    Ok(value
        .replace("${user.home}", &crate::get_user_home()?.to_string_lossy())
        .replace("${idea.home.path}", &ide_home.to_string_lossy()))
}

fn read_java_property(path: &Path, key: &str) -> Option<String> {
    let content = fs::read_to_string(path).ok()?;
    let mut logical_line = String::new();
    for physical_line in content.lines() {
        if logical_line.is_empty() {
            logical_line.push_str(physical_line);
        }
        else {
            logical_line.push_str(physical_line.trim_start_matches([' ', '\t', '\u{000c}']));
        }
        if has_continuation(&logical_line) {
            let _ = logical_line.pop();
            continue;
        }
        if let Some((actual_key, value)) = parse_java_property_line(&logical_line) {
            if actual_key == key {
                return Some(value);
            }
        }
        logical_line.clear();
    }
    if !logical_line.is_empty() {
        if let Some((actual_key, value)) = parse_java_property_line(&logical_line) {
            if actual_key == key {
                return Some(value);
            }
        }
    }
    None
}

fn has_continuation(line: &str) -> bool {
    line.as_bytes().iter().rev().take_while(|byte| **byte == b'\\').count() % 2 == 1
}

fn parse_java_property_line(line: &str) -> Option<(String, String)> {
    let line = line.trim_start_matches([' ', '\t', '\u{000c}']);
    if line.is_empty() || line.starts_with(['#', '!']) {
        return None;
    }

    let mut escaped = false;
    let mut separator = None;
    for (index, ch) in line.char_indices() {
        if escaped {
            escaped = false;
        }
        else if ch == '\\' {
            escaped = true;
        }
        else if ch == '=' || ch == ':' || ch.is_ascii_whitespace() {
            separator = Some(index);
            break;
        }
    }
    let key_end = separator.unwrap_or(line.len());
    let mut value_start = key_end;
    let bytes = line.as_bytes();
    while value_start < bytes.len() && bytes[value_start].is_ascii_whitespace() {
        value_start += 1;
    }
    if value_start < bytes.len() && (bytes[value_start] == b'=' || bytes[value_start] == b':') {
        value_start += 1;
    }
    while value_start < bytes.len() && bytes[value_start].is_ascii_whitespace() {
        value_start += 1;
    }

    Some((unescape_java_property(&line[..key_end])?, unescape_java_property(&line[value_start..])?))
}

fn unescape_java_property(value: &str) -> Option<String> {
    let mut result = String::with_capacity(value.len());
    let mut chars = value.chars();
    while let Some(ch) = chars.next() {
        if ch != '\\' {
            result.push(ch);
            continue;
        }
        match chars.next()? {
            't' => result.push('\t'),
            'n' => result.push('\n'),
            'r' => result.push('\r'),
            'f' => result.push('\u{000c}'),
            'u' => {
                let code = chars.by_ref().take(4).collect::<String>();
                if code.len() != 4 {
                    return None;
                }
                result.push(char::from_u32(u32::from_str_radix(&code, 16).ok()?)?);
            }
            escaped => result.push(escaped),
        }
    }
    Some(result)
}

fn read_overlay(config_dir: &Path, overlay_path: &Path) -> Result<Vec<String>> {
    validate_directory(config_dir)?;
    validate_directory(&config_dir.join("tbe"))?;
    validate_overlay(overlay_path)?;

    let metadata = fs::symlink_metadata(overlay_path)
        .context("Cannot inspect managed backend VM options overlay")?;
    if metadata.len() > MAX_OVERLAY_SIZE {
        bail!("Managed backend VM options overlay is too large")
    }
    let content = fs::read_to_string(overlay_path)
        .context("Cannot read managed backend VM options overlay as UTF-8")?;
    if content.is_empty() {
        return Ok(Vec::new());
    }
    let raw_options: Vec<&str> = content
        .strip_suffix('\n')
        .unwrap_or(&content)
        .split('\n')
        .collect();
    if raw_options.len() > MAX_OPTION_COUNT {
        bail!("Managed backend VM options overlay contains too many options")
    }

    let mut options = Vec::with_capacity(raw_options.len());
    for raw_option in raw_options {
        if raw_option.is_empty()
            || raw_option.len() > MAX_OPTION_LENGTH
            || raw_option.contains(['\0', '\r', '\n'])
        {
            bail!("Managed backend VM option cannot be applied")
        }
        options.push(raw_option.to_string());
    }
    Ok(options)
}

fn validate_directory(path: &Path) -> Result<()> {
    let metadata = fs::symlink_metadata(path)
        .context("Cannot inspect managed backend VM options directory")?;
    if !metadata.file_type().is_dir() || metadata.file_type().is_symlink() {
        bail!("Managed backend VM options directory is unsafe")
    }
    Ok(())
}

fn validate_overlay(path: &Path) -> Result<()> {
    let metadata =
        fs::symlink_metadata(path).context("Cannot inspect managed backend VM options overlay")?;
    if !metadata.file_type().is_file() || metadata.file_type().is_symlink() {
        bail!("Managed backend VM options overlay is unsafe")
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn missing_overlay_returns_an_empty_managed_layer() {
        let root = tempdir().unwrap();

        let actual = load_for_launch(
            &[],
            &root.path().join("missing-config"),
            root.path(),
        )
        .unwrap();

        assert!(actual.is_empty());
    }

    #[test]
    fn resolves_last_config_property() {
        let root = tempdir().unwrap();
        let first = root.path().join("first");
        let last = root.path().join("last");
        let actual = resolve_config_dir(
            &[
                format!("-Didea.config.path={}", first.display()),
                format!("-Didea.config.path={}", last.display()),
            ],
            &root.path().join("default"),
            &root.path().join("ide"),
        )
        .unwrap();
        assert_eq!(actual, last);
    }

    #[test]
    fn resolves_config_property_from_an_idea_properties_file() {
        let root = tempdir().unwrap();
        let default = root.path().join("default config");
        let configured = root.path().join("configured path");
        fs::create_dir(&default).unwrap();
        fs::write(
            default.join("idea.properties"),
            format!(
                "# comment\nother=value\nidea.config.path : {}\nidea.config.path=/ignored\n",
                java_property_path(&configured),
            ),
        )
        .unwrap();

        let actual = resolve_config_dir(&[], &default, root.path()).unwrap();

        assert_eq!(actual, configured);
    }

    #[test]
    fn prefers_a_custom_idea_properties_file() {
        let root = tempdir().unwrap();
        let default = root.path().join("default config");
        let configured = root.path().join("configured path");
        let custom_properties = root.path().join("custom idea.properties");
        fs::create_dir(&default).unwrap();
        fs::write(default.join("idea.properties"), "idea.config.path=/ignored\n").unwrap();
        fs::write(&custom_properties, format!("idea.config.path={}\n", java_property_path(&configured))).unwrap();

        let actual = resolve_config_dir(
            &[format!("-Didea.properties.file={}", custom_properties.display())],
            &default,
            root.path(),
        )
        .unwrap();

        assert_eq!(actual, configured);
    }

    #[test]
    fn parses_escaped_and_continued_java_properties() {
        let root = tempdir().unwrap();
        let properties = root.path().join("idea.properties");
        fs::write(
            &properties,
            "ignored=value\nidea\\.config\\.path=first\\\n  second\\ path\n",
        )
        .unwrap();

        assert_eq!(read_java_property(&properties, "idea.config.path").as_deref(), Some("firstsecond path"));
    }

    #[test]
    fn loads_arbitrary_overlay_options_in_server_order() {
        let root = tempdir().unwrap();
        let config_dir = root.path().join("config");
        fs::create_dir(&config_dir).unwrap();
        let overlay_dir = config_dir.join("tbe");
        fs::create_dir(&overlay_dir).unwrap();
        let overlay = overlay_dir.join("managed-backend.vmoptions");
        fs::write(
            &overlay,
            "-Didea.config.path=/tmp/other\n-Xmx4g\n-XX:+UseG1GC\n-javaagent:agent.jar\n-Dvalue.with.spaces=two values\n",
        )
        .unwrap();

        let actual = load_for_launch(
            &[
                format!("-Didea.config.path={}", config_dir.display()),
                "-Dalpha.option=base".into(),
                "-Dduplicate.option=first".into(),
                "-Dduplicate.option=last".into(),
            ],
            Path::new("/unused"),
            root.path(),
        )
        .unwrap();

        assert_eq!(
            actual,
            vec![
                "-Didea.config.path=/tmp/other",
                "-Xmx4g",
                "-XX:+UseG1GC",
                "-javaagent:agent.jar",
                "-Dvalue.with.spaces=two values",
            ],
        );
    }

    #[test]
    fn accepts_an_empty_overlay() {
        let root = tempdir().unwrap();
        let config_dir = root.path().join("config");
        let overlay = create_safe_overlay(&config_dir, b"");

        let actual = load_for_launch(
            &[format!("-Didea.config.path={}", config_dir.display())],
            Path::new("/unused"),
            root.path(),
        )
        .unwrap();

        assert!(actual.is_empty());
        assert_eq!(fs::metadata(overlay).unwrap().len(), 0);
    }

    #[test]
    fn rejects_malformed_and_oversized_overlays() {
        let root = tempdir().unwrap();
        let config_dir = root.path().join("config");
        let overlay = create_safe_overlay(&config_dir, b"-Dinitial=value\n");
        let invalid_contents = vec![
            b"\n".to_vec(),
            b"-Dvalid=value\n\n".to_vec(),
            b"-Dcarriage=value\r\n".to_vec(),
            b"-Dnul=value\0suffix\n".to_vec(),
            [vec![b'x'; MAX_OPTION_LENGTH + 1], vec![b'\n']].concat(),
            "-Dx=y\n".repeat(MAX_OPTION_COUNT + 1).into_bytes(),
            vec![b'x'; MAX_OVERLAY_SIZE as usize + 1],
            vec![0xFF, b'\n'],
        ];

        for content in invalid_contents {
            fs::write(&overlay, &content).unwrap();
            assert!(
                read_overlay(&config_dir, &overlay).is_err(),
                "accepted overlay of {} bytes",
                content.len(),
            );
        }
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn rejects_a_dangling_overlay_symlink() {
        use std::os::unix::fs::symlink;

        let root = tempdir().unwrap();
        let overlay_dir = root.path().join("tbe");
        fs::create_dir(&overlay_dir).unwrap();
        let overlay = overlay_dir.join("managed-backend.vmoptions");
        symlink(overlay_dir.join("missing.vmoptions"), &overlay).unwrap();

        assert!(load_for_launch(
            &[format!("-Didea.config.path={}", root.path().display())],
            Path::new("/unused"),
            root.path(),
        )
        .is_err());
    }

    fn create_safe_overlay(config_dir: &Path, content: &[u8]) -> PathBuf {
        fs::create_dir(config_dir).unwrap();
        let overlay_dir = config_dir.join("tbe");
        fs::create_dir(&overlay_dir).unwrap();
        let overlay = overlay_dir.join("managed-backend.vmoptions");
        fs::write(&overlay, content).unwrap();
        overlay
    }

    fn java_property_path(path: &Path) -> String {
        path.to_string_lossy().replace('\\', "\\\\")
    }
}
