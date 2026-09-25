// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/// Formats the duplicate report of one jar: `<jar>: N duplicate entries, first source wins: a, b`, with at most 10
/// names. It is not a failure. Two merged libraries can hold the same service file, and the first source wins. The
/// report makes a real collision visible in the action log, for example two module outputs with the same class.
pub fn duplicate_line(jar_name: &str, duplicates: &[String]) -> Option<String> {
    if duplicates.is_empty() {
        return None;
    }
    let count = duplicates.len();
    let noun = if count == 1 { "entry" } else { "entries" };
    let shown = duplicates[..count.min(10)].join(", ");
    Some(format!("{jar_name}: {count} duplicate {noun}, first source wins: {shown}"))
}
