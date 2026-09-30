// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! Matches an entry name against a `java.nio` `glob:` pattern of the subset that the dev-dist plan uses.
//!
//! The subset has literals, `*`, `**` and `{a,b}` groups, with the JDK meaning of each. `*` matches zero or more
//! characters other than `/`. `**` matches zero or more characters across `/`, but no line terminator, as the JDK `.*`
//! does. A group matches one of its alternatives, and an alternative can hold `*` and `**`. A match is case-sensitive
//! and covers the whole name, as `PathMatcher.matches` does.
//!
//! `compile` refuses `?`, `[`, `]`, `\`, a nested group and an unbalanced brace. The plan uses none of them. Thus a
//! new construct in a plan fails the build and does not match differently from the JDK.
//!
//! The crate translates a pattern into a regular expression, as `sun.nio.fs.Globs.toUnixRegexPattern` does. It does
//! not use `globset`, because `globset` also matches `**/setup.py` against `setup.py`, and the JDK does not. The plan
//! relies on that difference: it lists `**/{setup.py,conftest.py}` and `{setup.py,conftest.py}`.

use anyhow::{Result, anyhow};
use regex::Regex;

/// One compiled `glob:` pattern.
#[derive(Clone, Debug)]
pub struct JavaGlob {
    regex: Regex,
}

impl JavaGlob {
    /// Parses a glob pattern of the subset. The error names the pattern and the problem with its character index.
    pub fn compile(pattern: &str) -> Result<Self> {
        let expression = translate(pattern).map_err(|problem| {
            anyhow!("glob {pattern:?}: the dev-dist plan supports only *, ** and {{a,b}}, but the pattern has {problem}")
        })?;
        let regex = Regex::new(&expression).expect("a translated pattern is a valid regular expression");
        Ok(Self { regex })
    }

    /// Tells whether the whole name matches.
    ///
    /// The matcher first removes one trailing `/` of a clean name, which has no repeated `/`. The JDK matches such a
    /// name without that `/`.
    pub fn matches(&self, name: &str) -> bool {
        let name = if name.len() > 1 {
            name.strip_suffix('/').unwrap_or(name)
        } else {
            name
        };
        self.regex.is_match(name)
    }
}

/// `**`: the JDK `.*` without `DOTALL`, which matches no line terminator.
const ANY_ACROSS_NAMES: &str = r"[^\n\r\x{85}\x{2028}\x{2029}]*";

/// Translates the pattern into an anchored regular expression, or returns the problem with the character index where
/// it starts.
fn translate(pattern: &str) -> Result<String, String> {
    let mut expression = String::from("^");
    // The index of the `{` of the open group.
    let mut group: Option<usize> = None;
    let mut characters = pattern.chars().enumerate().peekable();
    while let Some((index, character)) = characters.next() {
        match character {
            '*' if characters.next_if(|&(_, next)| next == '*').is_some() => expression.push_str(ANY_ACROSS_NAMES),
            '*' => expression.push_str("[^/]*"),
            '{' if group.is_some() => return Err(format!("a nested '{{' at {index}")),
            '{' => {
                group = Some(index);
                expression.push_str("(?:");
            }
            '}' => match group.take() {
                Some(_) => expression.push(')'),
                None => return Err(format!("a '}}' with no '{{' at {index}")),
            },
            ',' if group.is_some() => expression.push('|'),
            '?' | '[' | ']' | '\\' => return Err(format!("'{character}' at {index}")),
            other => expression.push_str(&regex::escape(other.encode_utf8(&mut [0; 4]))),
        }
    }
    if let Some(index) = group {
        return Err(format!("no '}}' for the '{{' at {index}"));
    }
    expression.push('$');
    Ok(expression)
}

#[cfg(test)]
mod tests;
