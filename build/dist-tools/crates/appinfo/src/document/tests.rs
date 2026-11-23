// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::Path;

use crate::{ApplicationInfo, Replacement};

fn owned(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

#[test]
fn a_replacement_keeps_every_equals_sign_after_the_key() {
    assert_eq!(Replacement::parse("B=x = y").unwrap(), Replacement::new("B", "x = y"));
    assert_eq!(Replacement::parse("A=").unwrap(), Replacement::new("A", ""));
    assert_eq!(
        Replacement::parse_all(&owned(&["B=1", "A=2"])).unwrap(),
        [Replacement::new("B", "1"), Replacement::new("A", "2")]
    );
}

#[test]
fn a_replacement_needs_a_key_and_a_new_one() {
    for (value, want) in [
        ("VALUE", r#"a replacement is '<key>=<value>', and "VALUE" is not"#),
        ("=value", r#"a replacement is '<key>=<value>', and "=value" is not"#),
    ] {
        assert_eq!(format!("{:#}", Replacement::parse(value).unwrap_err()), want);
    }
    let error = Replacement::parse_all(&owned(&["A=1", "B=2", "A=3"])).unwrap_err();
    assert_eq!(format!("{error:#}"), r#"the replacement "A" is stated more than once"#);
}

#[test]
fn a_missing_application_info_names_the_file() {
    let error = ApplicationInfo::load(Path::new("absent/ApplicationInfo.xml"), &[], None, 0).unwrap_err();
    let text = format!("{error:#}");
    assert!(text.starts_with("cannot read absent/ApplicationInfo.xml: "), "{text}");
}
