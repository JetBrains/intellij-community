---
name: compose-ui-architecture
description: Write Compose Desktop UI state, effects, and presenter boundaries.
---

# Compose UI Architecture

Use this skill to keep Compose Desktop UI code architecturally correct while editing IntelliJ-style desktop surfaces.
This skill provides authoring and review guardrails for individual changes. When the user asks for a systemic Compose UI
architecture assessment across a codebase, use `compose-ui-audit` instead.

For general post-change review or cleanup, use the review subagents available in the current harness instead of
stretching this skill beyond UI architecture. If subagent delegation is unavailable, run those review passes
sequentially yourself.

Do not use this skill to replace project docs. If the project has architecture docs, treat them as authoritative when
details conflict.

## Pair With / Defer To

Pair it with:

- Pair this skill with `jewel-ui` when selecting Jewel components, theme wrappers, icons, typography, and
  IntelliJ-style controls.
- Pair this skill with `jewel-swing-interop` when configuring Swing/AWT hosting, `ComposePanel`, `LocalComponent`,
  popups, and bridge theme mechanics.

Defer these adjacent tasks instead of stretching this skill:

- Use `jewel-ui` for component choice, label rules, icons, theme wrappers, typography, and Jewel visual style.
- Use `jewel-swing-interop` for `ComposePanel`, AWT popup forwarding, `LocalComponent`, and host Swing component access.
- Use configured review or refactoring subagents for general post-change code review or cleanup, or run those passes
  sequentially yourself if delegation is unavailable.
- Use `compose-ui-audit` for a whole-surface systemic Compose UI architecture assessment.

## Desktop Compose Terminology

Be precise with names:

- In Android docs, `ViewModel` roughly maps to a desktop UI **presenter/coordinator** layer.
- In desktop Compose code, a **view model** may also mean a view-layer data model / screen projection, such as
  `ScreenState`, `RowModel`, or `SurfaceModel`. Avoid ambiguity by naming whether you mean lifecycle owner or render
  model.
- Do not introduce Android `ViewModel`, Hilt/Koin, Android lifecycle, or Navigation guidance into desktop Compose UI
  unless the project already uses those concepts.

## Core Rule

**Compositions render. Presenters decide.**

Composables may own ephemeral visual state: hover, focus, animation progress, expansion toggles, tooltip visibility,
and layout wiring. They must not own app state, persistence, RPC, file/process/network work, JSON parsing, model
selection, permission policy, review workflow decisions, or workspace/config lookup.

Route those through presenters/coordinators or equivalent state-owning layers and expose ready-to-render view models to
UI.

## Fast Guardrail Pass

Before returning Compose UI code, silently check these questions:

1. **Boundary:** does the composable render inputs, or does it decide business/app behavior?
2. **State:** is local state truly ephemeral? If state mirrors app/session/project settings, move it up.
3. **Projection:** should several values be combined into one presenter-owned screen state before UI sees them?
4. **Effects:** does every effect stay minimal and key on the values that should restart it?
5. **Modifier:** do reusable composables accept and forward `modifier: Modifier = Modifier` to the outermost layout?
6. **Lists:** do movable and heterogeneous lazy-list items pass a stable `key =` parameter and a matching
   `contentType =` parameter?
7. **Performance:** does composition avoid expensive transforms, object churn, and broad state reads?
8. **Animation:** does the animation code use declarative APIs where possible, wrap stateful animation objects in
   `remember`, and read per-frame values in the layout or draw phase?
9. **API shape:** do reusable components accept immutable data and event callbacks instead of presenter instances or
   mutable state objects?

If an answer is no, fix the shape before writing more code rather than documenting the violation.

Also check for false positives. Local hover state, focus requesters, text-field state, expansion toggles, scroll
commands, and one-shot UI labels are usually fine in composition when they do not shadow app/session state.

## Progressive Reference Files

Read only the reference files needed for the current task:

- Read `references/state-boundaries.md` for rules on presenter/coordinator vs. state-holder vs. composition boundaries,
  desktop lifecycle, flow collection, `CompositionLocal` guardrails, and testing ownership.
- Read `references/effects.md` for rules on `remember`, `LaunchedEffect`, `DisposableEffect`, `snapshotFlow`, coroutine
  scopes, and effect keys.
- Read `references/animation.md` when choosing animation APIs, remembering `Animatable` state, launching target-driven
  effects, deferring animated reads, or using `AnimatedContent`, infinite transitions, and gesture-driven motion.
- Read `references/component-api-lists-performance.md` for rules on reusable composable API shape, modifier contracts,
  lazy-list keys and content types, recomposition and performance smells, and healthy patterns to preserve.
- Read `references/desktop-seams-review-output.md` for guidance on desktop dialogs, popups, shortcuts, clipboard and URI
  opening, window state, protocol and runtime seams, and the file-grounded review output format.

## Review Output

When reviewing code with this skill, keep output short and file-grounded:

```markdown
### path/to/File.kt
- Issue: [rule name]
- Why it matters: [one sentence]
- Fix: [specific direction or tiny before/after]
```

End with the top three fixes, highest impact first. Do not list clean files.
