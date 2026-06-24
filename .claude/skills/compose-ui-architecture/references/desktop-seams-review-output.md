# Desktop Seams And Review Output

Use this reference when UI work touches desktop-specific integration points or when preparing review output for this
skill.

## Desktop Surface Checklist

When touching desktop-specific UI surfaces, check these extra seams:

- **Dialogs and file pickers:** composition may show/request them, but selected paths/settings mutations go through
  callbacks and presenter APIs.
- **Popups:** Jewel popups can involve real AWT windows; keep click-through or host-component mechanics isolated and
  documented with `jewel-swing-interop` patterns.
- **Keyboard shortcuts:** bind at the shell/screen level, route intent through presenter/app state, and keep platform
  differences explicit.
- **Clipboard/open URI:** access through a small environment/callback seam so tests can fake it.
- **Window state:** size, split panes, scroll position, focus, and hover are UI-local; persisted session/window policy
  is presenter/app state.
- **Protocol/runtime-backed surfaces:** UI renders presenter-projected snapshots and sends callbacks upward; protocol
  decode, validation, compilation, subprocess/runtime ownership, and persistence stay outside composition.

## Review Output

When reviewing code with this skill, keep output short and file-grounded:

```markdown
### path/to/File.kt
- Issue: [rule name]
- Why it matters: [one sentence]
- Fix: [specific direction or tiny before/after]
```

End with the top three fixes, highest impact first. Do not list clean files.
