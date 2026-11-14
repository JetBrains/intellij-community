// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use crate::{Item, Tone, mark, message};

#[test]
fn a_message_starts_with_the_mark_of_its_tone_and_keeps_its_text() {
    for tone in [Tone::Info, Tone::Success, Tone::Warning, Tone::Error, Tone::Detail] {
        let line = message(tone, "the text");
        assert!(line.contains(mark(tone)), "{line:?}");
        assert!(line.contains("the text"), "{line:?}");
    }
}

#[test]
fn a_menu_row_shows_its_label() {
    let item = Item {
        label: "JetBrains Light › JetBrains IDE".to_owned(),
        answer: "JetBrains IDE".to_owned(),
    };
    assert_eq!(item.to_string(), "JetBrains Light › JetBrains IDE");
}
