# Label and Writing Rules

Writing conventions for component labels, messages, and action text. These rules are shared across Jewel's controls — checkboxes, radios, buttons, links, group headers, tooltips, helper text — and carry IntelliJ-specific conventions that don't always match general UX defaults. Apply them explicitly.

## Writing Component Labels

1. **Sentence-style capitalization for most controls** (checkboxes, radios, links, group headers, tooltips, helper text). **Exception: `DefaultButton` / `OutlinedButton` / `DefaultSplitButton` / `OutlinedSplitButton` use title case** — `"Save Changes"`, not `"Save changes"`. The button case exception is an IntelliJ/Jewel convention; do not default to sentence case for buttons.
2. No ending punctuation, **except** group labels (radio / checkbox group headers), which end with `:`.
3. Imperative verb form.
4. Avoid negation. Exception: `"Do not show again"`.
5. Keep labels short; wrap to at most two lines.
6. Checkbox label always on the right of the box; in tables, put it in the column header — don't repeat on every row.
7. **Button labels never include `"Now"`** — `"Apply"`, not `"Apply Now"`; `"Save"`, not `"Save Now"`. A button is implicitly immediate; `"Now"` adds no information.
8. **Placeholders are not labels.** Never rely on a `TextField` placeholder to carry the field's purpose — placeholders hide as the user types. Always pair a field with a visible label above or to the left; use the placeholder for example input only (`"name@example.com"`, not `"Email address"`).
9. **Link text must be descriptive.** Do **not** use `"click here"`, `"learn more"`, `"navigate"`, or similar bare phrasings as `Link` text. The link itself implies action; the text should name the destination (`"Open documentation"`, `"View the migration guide"`).
10. **External-link icon**: append a trailing arrow (↗) or external-link icon exclusively on `Link`s that leave the app. Internal navigation links within the same window get no icon.

## Canonical Source Links

Authority for label conventions: JetBrains IntelliJ Platform UI Guidelines — *Writing UI Texts* and the per-component *Label/UX rules* sections.

- [Writing Short and Clear](https://plugins.jetbrains.com/docs/intellij/writing-short.html)
- [Capitalization](https://plugins.jetbrains.com/docs/intellij/capitalization.html)
- [Punctuation](https://plugins.jetbrains.com/docs/intellij/punctuation.html)
- [Radio Button](https://plugins.jetbrains.com/docs/intellij/radio-button.html) — radio labels + group-header colon rule
- [Checkbox](https://plugins.jetbrains.com/docs/intellij/checkbox.html) — checkbox labels + negation exception
- [Button](https://plugins.jetbrains.com/docs/intellij/button.html) — title-case + no "Now" + max 5 words
- [Input Field](https://plugins.jetbrains.com/docs/intellij/input-field.html) — placeholder-is-not-a-label rule
- [Link](https://plugins.jetbrains.com/docs/intellij/link.html) — descriptive link text + external-link arrow
