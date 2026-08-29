---
name: migrate-settings-to-compose-swing
description: Move a Swing settings page to Compose Swing, with previews and tests.
---

# Migrating Swing settings pages

Extract the UI into declarative Compose Swing functions: state down, events up.
Keep the configurable's transaction and application order.

Read [kotlin-ui-dsl.md](../../../platform/compose/swing-ui/docs/kotlin-ui-dsl.md),
[README.md](../../../platform/compose/swing-ui/README.md), and
[raw-swing.md](../../../platform/compose/swing-ui/docs/raw-swing.md).
Read the library's [component state](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/COMPONENT-STATE.md)
and [testing](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/TESTING-COMPONENTS.md) contracts.

## Steps

Preserve unrelated files. Report unrelated renderer failures separately.

1. Inventory every page, nested editor, and extension-provided form in the requested screen.
   Identify its model, UI owner, validation, search, and Apply, Reset, and Cancel behavior.
2. Separate the controller from the form before changing layout.
   The controller reads the working model into state and handles events.
   The composable receives values and callbacks. It must render without a live project model.
   Use content slots for specialized controls that need platform services.
3. Build one composition at the page boundary. Use the existing owner's lifetime.
   Child forms are composables in that composition; they take no disposable and create no host.
   Preserve configurables that own navigation, shared models, or application order.
   Use `ComposeSwingSearchableConfigurable` for a standalone page without those contracts.
4. Use the base library's declarative controls for buttons, labels, text, and choices.
   Replace control-field reads with state reads.
   A programmatic Reset updates state. It does not publish a user edit.
   A migration creates no new components.
   When no library or IntelliJ component covers a control, stop and report it as a missing component.
   A new component is a reusable composable in the IntelliJ components package.
   It follows every rule of the library's [custom components](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/CUSTOM-COMPONENTS.md) guide.
5. Rebuild a Kotlin UI DSL `panel { }` with `FormPanel`. A page without the DSL keeps its layout structure.
   Preserve bundle keys, spacing, focus, labels, accessibility, and validation.
   Derive enabled and visible regions from state. Use one authority for each property.
   Keep hidden controls in the composition when their identity or state must survive.
6. Add zero-argument composables with `org.jetbrains.compose.swing.tooling.Preview` beside each production form.
   Preview state and callbacks must call the same form. Include inherited values and disabled regions. Use the default preview size.
   A preview must not construct a project, configurable, transaction, or composition host.
   Use the preview to expose dependencies that belong in the controller.
7. Test each migrated form in `runComposeSwingTest`, with the form passed to its `setContent`.
   Drive user actions, adopt callbacks into state, and assert the rendered result after recomposition.
   Test Reset, enabled regions, selection, labels, and value preservation.
   Test Apply, Cancel, model validation, disposal, and rebuilding through the owner's public API.
   Lifecycle tests alone do not verify the form. Confirm that the runner executed the intended tests.
8. Open and interact with the previews in the IDE. Compare the live pages with the original.
   Check spacing, focus traversal, accessibility, keyboard actions, resizing, and the IDE look and feel.
   The running IDE must use the same form APIs as the compiled source.
   Report an API mismatch with the required IDE build; do not change the form to fit a stale runtime.

## Rules

- **State.** Each value has one owner. Hoist shared values and derive presentation from them.
  Preserve staged values across navigation. Key repeated editors by model identity.
  Do not cache model values with `remember` when Reset or another page can change them.
- **Transactions.** Keep edits in the existing working model.
  Use `bind(...)` for persisted settings owned by `ComposeSwingSearchableConfigurable`.
  Preserve property transformations and change notifications.
  A standalone configurable calls `requestModifiedCheck()` for state changes without a user gesture.
- **Lifecycle.** Dispose the root composition with its UI owner. Rebuilding creates a new composition.
  Do not dispose on temporary detachment. Do not add a disposable for each child form.
  An override of `ComposeSwingSearchableConfigurable.disposeUIResources` must call `super`.
- **Threading.** Reach PSI, VFS, documents, and the project model from a `LaunchedEffect`.
- **Validation.** Keep input-time and apply-time checks.
  Use `ConfigurationException` for apply-time refusal and the error-outline modifier for field errors.
- **Search.** A composition host waits for a window and is empty during offscreen traversal.
  Use the standalone configurable's immediate composition when that traversal is required.
  Index conditionally absent strings with a `SearchableOptionContributor` when they must remain findable.
- **Verification.** The headless harness verifies state, callbacks, and structure.
  Verify painting, preferred sizes, attach-scoped behavior, and visual parity in a realized IDE window.
  Report unconverted pages and unavailable checks. A wrapper around an unchanged form is not a completed page.
