# What ModCommands can and cannot do

Source of truth: `ModCommand.java` (`community/platform/analysis-api/src/com/intellij/modcommand/ModCommand.java`)
and its siblings in `community/platform/analysis-api/src/com/intellij/modcommand/`.

`ModCommand` is a **sealed interface**, and every implementation is a **record**. That is what makes a
command inspectable and serializable — it can be sent over the network or to another process, and a
custom executor can extract exactly what will happen. It also means the capability set is closed: if no
command models what your fix needs, the fix cannot be converted (see the triage in
[../SKILL.md](../SKILL.md)).

## The commands

Taken from the `permits` clause of `ModCommand`.

| Command | What it does |
|---|---|
| `ModNothing` | nothing (`ModCommand.nop()`) |
| `ModCompositeCommand` | several leaf commands in sequence; must not nest composites |
| `ModUpdateFileText` | replace fragments of one file's text — the workhorse produced by `psiUpdate` |
| `ModCreateFile` | create a file or directory with given content |
| `ModDeleteFile` | delete a file |
| `ModMoveFile` | move and/or rename a file |
| `ModDisplayMessage` | show a localized message (`INFORMATION` or `ERROR`) |
| `ModNavigate` | set caret position and selection; may open an editor |
| `ModHighlight` | highlight ranges; no-op if no editor is open |
| `ModChooseAction` | show a chooser and continue with the selected `ModCommandAction` |
| `ModEditOptions` | show an options UI, then continue based on the edited container |
| `ModCopyToClipboard` | put a string on the OS clipboard |
| `ModOpenUrl` | open a URL in the browser |
| `ModShowConflicts` | show conflicts and require confirmation. **Not executed in batch; skipped in preview** |
| `ModStartTemplate` | start a live template. In batch, fields get their default values |
| `ModStartRename` | invoke the Rename UI for a symbol. **Ignored in batch** |
| `ModUpdateReferences` | update references to a changed declaration via 'suggested refactoring'; may silently do nothing per language |
| `ModUpdateSystemOptions` | change options reachable through `OptionControllerProvider` (notably inspection settings) |
| `ModLaunchEditorAction` | run an interactive editor action by id (e.g. `CodeCompletion`) in the opened editor |
| `ModRegisterTabOut` | register a tab-out range in the editor |

## Prefer the `ModCommand` statics

The static factories on `ModCommand` cover most cases and keep you insulated from record shape changes,
so prefer them when one models the command you need. Construct a command record directly when no factory
exists — notably a general `ModEditOptions`, or `ModMoveFile` when renaming a file. Follow current
in-repo examples such as `EditContractIntention` and `RenameFileModCommand` for those exceptions.

| Factory | Purpose |
|---|---|
| `psiUpdate(ActionContext, Consumer<ModPsiUpdater>)` | the general form: edit non-physical copies, get a command back |
| `psiUpdate(E orig, Consumer<E>)` / `psiUpdate(E orig, BiConsumer<E, ModPsiUpdater>)` | element-anchored shorthands |
| `insertText(ActionContext, String, boolean moveAfter)` | insert text at the context offset |
| `nop()`, `info(msg)`, `error(msg)` | no-op / message commands |
| `select(PsiElement)`, `moveTo(PsiElement)` | navigation for an element you are *not* modifying |
| `highlight(PsiElement...)`, `highlight(TextAttributesKey, PsiElement...)` | highlighting, likewise |
| `copyToClipboard(String)`, `openUrl(String)` | clipboard / browser |
| `chooseAction(title, List<ModCommandAction>)` | the chooser popup |
| `psiUpdateStep(element, title, action[, range])` | build a chooser child action in one call |
| `psiBasedStep(element, title, function, range)` | same, when the child builds its own command |
| `chooseMultipleMembers(title, elements[, defaultSelection], next)` | multi-select member chooser |
| `showConflicts(Map<PsiElement, Conflict>)` | conflicts gate; returns `nop()` for an empty map |
| `moveFile(VirtualFile, VirtualFile targetDir)` | move a file |
| `updateOption(context, bindId, value)`, `updateOptionList(...)`, `updateInspectionOption(context, inspection, updater)` | settings |
| `moveCaretAfter(command, file, offset, leanRight)` | append navigation to an offset, adjusted for the command's own edits |

Compose with **`andThen`**:

```java
return showConflicts(conflicts).andThen(psiUpdate(modifierList, MakeClassFinalFix::doMakeFinal));
```

`andThen` flattens composites and, for `ModChooseAction`/`ModEditOptions`, appends the continuation to
each branch rather than after the chooser.

## Capability matrix

**Supported.** Editing, creating, deleting, moving and renaming files; caret, selection, highlighting;
live templates (basic) and the standard Rename UI (inline or dialog, depending on the registered
`Renamer`); information and error messages; conflicts confirmation;
choosers (single-choice and multi-select members); clipboard; opening a URL; inspection and
`OptionControllerProvider`-reachable settings; 'suggested refactoring' reference updates; launching a
named editor action.

**Limited.** Templates support the `TemplateBuilder`-style subset only. `ModUpdateReferences` /
`trackDeclaration` covers the suggested-refactoring path, not a real change-signature. The member
chooser lacks extras of the Swing `MemberChooser` (e.g. no 'copy javadoc' button).
`ModUpdateSystemOptions` is `@ApiStatus.Experimental`.

**Not supported — do not convert a fix that needs these.** Arbitrary UI dialogs (apart from the modeled
Rename UI, chooser, options form, or conflicts list); project model / Gradle / Maven / SDK /
language-level configuration; multi-step refactoring engines; **adding** a new Java external annotation
(`ModCommandAwareExternalAnnotationsManager` edits and removes only, because adding may require
configuring an annotation root); launching external programs.

## Execution modes

The same command behaves differently per mode, so navigation and other modeled interactive effects
normally need no `isOnTheFly` branch. This does not apply to branches that change PSI edits,
availability, search scope, or other semantics. Executors live behind
`ModCommandExecutor` (`community/platform/analysis-api/src/com/intellij/modcommand/ModCommandExecutor.java`).

- **Interactive** (`executeInteractively`) — everything runs. Requires EDT and no write lock.
- **Batch** (`executeInBatch` → `BatchExecutionResult`) — navigation, highlighting, `ModShowConflicts`
  and `ModStartRename` are dropped. A `ModChooseAction` auto-selects its **first** option. A
  `ModEditOptions` proceeds with defaults only if `canUseDefaults` is true, otherwise the action is
  unavailable in batch. Override `ModCommandAction.availableInBatchMode()` to opt out entirely.
- **Preview** (`getPreview`, via `IntentionPreviewUtils.getModCommandPreview`) — derived from the
  command, so it can show multi-file changes, navigation and messages. `ModShowConflicts` is skipped and
  a chooser's **first** option is assumed.
- **On a file copy** (`executeForFileCopy`) — applies a single-file command to a non-physical copy,
  which is how one action can be composed into another.

Two consequences worth designing around:

1. **Order chooser options safest-first**, because batch and preview both pick the first one.
2. Guard genuinely expensive conflict computation with `IntentionPreviewUtils.isIntentionPreviewActive()`
   — preview ignores `ModShowConflicts` anyway, so computing it there is wasted work. See
   `MakeClassFinalFix` (`community/java/java-impl/src/com/siyeh/ig/fixes/MakeClassFinalFix.java`).

You do not need to special-case a chooser with a single option: it is executed directly without showing
UI.

## Renamed and removed API

These spellings no longer exist, but still turn up in older code and write-ups:

| Gone | Use instead |
|---|---|
| `ModCommands` facade (`ModCommands.psiUpdate`) | `ModCommand.psiUpdate` — every static lives on `ModCommand` |
| `ModChooseMember` | `ModCommand.chooseMultipleMembers`, backed by `ModEditOptions` |
| `asQuickFix()` | `LocalQuickFix.from(ModCommandAction)` (`asIntention()` still exists) |
| `ModPsiNavigator`, `ModPsiNavigator.fromEditor` | `ModNavigator` (`com.intellij.openapi.editor`); adapt with `editor.asModNavigator()` |

Newer additions, in case a write-up says they are missing: `ModOpenUrl` / `ModCommand.openUrl` (opening a
browser **is** supported), `ModMoveFile`, `ModLaunchEditorAction`, `ModRegisterTabOut` and
`LocalQuickFixWithModCommandFallback`.
