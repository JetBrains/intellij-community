# Working inside `psiUpdate`

`ModCommand.psiUpdate` is how most converted fixes are written — roughly 90–95%; the rest merely show a
message, navigate, copy to clipboard, update a system option, and the like. It:

1. creates a **non-physical copy** of the file containing the element,
2. calls your lambda with a copy of that element (and, optionally, a `ModPsiUpdater`),
3. tracks every change you make to the copy,
4. turns the diff into a `ModUpdateFileText` (or a composite, if you touched several files).

You never see the resulting command in the simple bases: `PsiUpdateModCommandQuickFix` and
`PsiUpdateModCommandAction` call `psiUpdate` for you.

## Which overload

| Overload | Use when |
|---|---|
| `psiUpdate(E orig, Consumer<E>)` | you only need to edit around one element |
| `psiUpdate(E orig, BiConsumer<E, ModPsiUpdater>)` | plus caret/template/rename/other files |
| `psiUpdate(ActionContext, Consumer<ModPsiUpdater>)` | the starting point is the caret/selection rather than one element; `updater`'s initial caret offset comes from the context |

With the element-anchored overloads the updater's initial caret offset is 0, not the real caret — take
the caret from `ActionContext` if you need it.

## `ActionContext`

`ActionContext` (`community/platform/analysis-api/src/com/intellij/modcommand/ActionContext.java`) is a
record and the **only** source of "where was this invoked" information. There is no `Editor`.

```java
record ActionContext(Project project, PsiFile file, int offset, TextRange selection, PsiElement element)
```

- `ActionContext.from(descriptor)` — for a quick-fix: `offset` is the **start of the highlighted range**,
  `selection` is the highlighted range, `element` is `descriptor.getStartElement()`.
- `ActionContext.from(editor, file)` — at the boundary where an editor still exists; a null editor gives
  offset 0 and an empty selection.
- `findLeaf()` / `findLeafOnTheLeft()` — the leaf at / just before the caret.
- `mapToInjected(injectedFile)` — rebase the context onto an injected file.
- `withFile` / `withElement` / `withOffset` / `withSelection` — derive a modified context.

## `ModPsiUpdater`

`ModPsiUpdater` (`community/platform/analysis-api/src/com/intellij/modcommand/ModPsiUpdater.java`)
extends `ModNavigator`. It has a notion of a **current file** — the file of the starting element — and
several methods retarget it if you pass an element from a different file.

`ModNavigator` is the small, shared set of navigation/editor operations that lets one utility method
serve both worlds: new callers pass the `ModPsiUpdater` they already hold, and old callers holding a real
`Editor` adapt it with `Editor.asModNavigator()`. So when a public helper needs only those operations,
type its parameter as `ModNavigator` to keep supporting `Editor`-based callers (see the
`FixDocCommentAction` example at the end of this file).

### Reaching other files

```java
<E extends PsiElement> E getWritable(E element)   // throws IllegalStateException
@NotNull PsiFile getOriginalFile(@NotNull PsiFile copyFile)
```

**Call `getWritable` for every element you intend to modify before you modify anything.** It throws
`IllegalStateException` if the element's file already has a modified copy. A `PsiDirectory` may be passed
too, but the copy only supports creating new files inside it.

`getOriginalFile` maps a writable copy back to the physical file.

### Editor state (all no-ops in batch — never branch on `isOnTheFly`)

```java
void moveCaretTo(int offset);  void moveCaretTo(PsiElement element);
void select(TextRange range);  void select(PsiElement element);
void highlight(PsiElement element);  void highlight(PsiElement, TextAttributesKey);
void highlight(TextRange, TextAttributesKey);
```

Offsets are adjusted automatically if you keep editing after positioning the caret.

### Query methods

These only read state, so they work in every mode — unlike the editor-state calls above they are not
no-ops in batch:

```java
int getCaretOffset();    // current caret; initial value from ActionContext, else 0
Document getDocument();  // the writable copy's document
PsiFile getPsiFile();    // the current (writable copy) file
Project getProject();
```

### Interactive follow-ups

```java
void rename(PsiNameIdentifierOwner element, List<String> suggestedNames);
void rename(PsiNamedElement element, @Nullable PsiElement nameIdentifier, List<String> suggestedNames);
ModTemplateBuilder templateBuilder();
void editorAction(String actionId, boolean optional);       // experimental
void trackDeclaration(PsiElement declaration);              // experimental
```

- `rename` starts the Rename UI; passing a null `nameIdentifier` to the second overload is discouraged,
  since some executors then skip the rename.
- `trackDeclaration` must be called **before** you change the declaration; it produces
  `ModUpdateReferences` ('suggested refactoring').
- `editorAction` ids are the IDE action ids; the tested ones are constants on `ModLaunchEditorAction`.

### Messages, conflicts, bailing out

```java
void message(String message);
void showConflicts(Map<PsiElement, ModShowConflicts.Conflict> conflicts);
void cancel(String errorMessage);
```

- `showConflicts` accumulates across calls, and its conflicts are shown **before** any modification —
  so the elements you pass must be physical or already-obtained writable copies, and you must not have
  modified PSI yet. If the user cancels, nothing is applied.
- `cancel` discards everything done so far and shows an error instead; subsequent updates are ignored.
  This is the replacement for showing an error balloon and returning.

## `ModTemplateBuilder`

From `updater.templateBuilder()`
(`community/platform/analysis-api/src/com/intellij/modcommand/ModTemplateBuilder.java`) —
usually 1–3 lines where the old code needed an `Editor`, a PSI commit and a physicality check:

- `field(element, expression)` / `field(element, varName, expression)` /
  `field(element, rangeInElement, varName, expression)` / `field(element, value)` — expression fields
- `field(element, varName, dependantVariableName, alwaysStopAt)` and its range/default-value variants —
  dependent (mirrored) fields
- `finishAt(offset)` — where the caret lands when the template completes
- `required()` — makes the action unavailable in non-interactive execution
- `onTemplateFinished(PsiFile -> ModCommand)` — a follow-up command once the template is done

## Non-physical PSI

Inside the lambda everything is non-physical, so old physicality checks are meaningless:

- `isPhysical()` branches — delete them. If the branch existed for intention preview, it is redundant now;
  when you genuinely still need to detect preview, call `IntentionPreviewUtils.isIntentionPreviewActive()`
  (`com.intellij.codeInsight.intention.preview.IntentionPreviewUtils`) rather than an `isPhysical()` check.
- "is this element synthetic?" → `element instanceof SyntheticElement`
- "was this created by a factory, detached from a file?" →
  `element.getContainingFile() instanceof DummyHolder`

Normal PSI utilities work on the copies: `CodeStyleManager.reformat`,
`JavaCodeStyleManager.shortenClassReferences`, element factories, `PsiElement.replace/add/delete`.

## Raw document edits

**Editing the copy's `Document` text directly is allowed.** This surprises people, so it is worth
spelling out: a fix does not have to express everything through PSI.

```java
Document doc = copyFile.getFileDocument();          // or updater.getDocument()
doc.replaceString(range.getStartOffset(), range.getEndOffset(), newText);
PsiDocumentManager.getInstance(project).commitDocument(doc);   // required, keep it
// ... PSI work on copyFile from here on
```

Why it works, from `PsiUpdateImpl.FileTracker`
(`community/platform/analysis-impl/src/com/intellij/lang/impl/modcommand/PsiUpdateImpl.java`):

- the tracker registers a `DocumentListener` on the copy's document and turns every change into a
  `ModUpdateFileText.Fragment`, and `getUpdateCommand()` derives the final text from
  `myTargetFile.getFileDocument().getText()` — so raw edits land in the resulting `ModUpdateFileText`
  exactly like PSI edits, and the intention preview shows them;
- the copy is backed by a free-threaded `LightVirtualFile`, so its document has threading/write
  assertions disabled — that is why a raw write does not blow up in the background read action;
- the framework does it itself: `FileTracker` deletes the selection with
  `myDocument.deleteString(...)` + `myManager.commitDocument(myDocument)` before handing you the copy.

Shipped examples: `PostfixModExpander.deleteTemplateKeyAndCommit`
(`community/platform/lang-impl/src/com/intellij/codeInsight/template/postfix/templates/PostfixModExpander.java`)
and `SpringUpdateSchemaIntention.updateSchema`
(`plugins/spring/spring-framework/spring-core/src/com/intellij/spring/ide/code/refactoring/SpringUpdateSchemaIntention.java`).

### Rules

1. **Write to the copy's document, never the physical one.** `PsiDocumentManager.getDocument(copyFile)`
   returns `null` for a non-physical copy — use `PsiFile.getFileDocument()` or `updater.getDocument()`.
   Reaching a physical document (via `FileDocumentManager`, an `Editor`, or the original file) is a real
   violation: `perform` must not touch physical state.
2. **Commit before the next PSI read.** The tree is stale until you do, and a stale tree silently
   produces wrong offsets rather than an exception.
3. **Do not assume your PSI references survive the commit.** In practice the incremental reparse patches
   the tree in place and reuses unchanged nodes, so elements usually stay valid — but that depends on the
   language's reparse granularity, so it is not a contract. If you need an element across the commit,
   take a `SmartPointerManager.createPointer(element)` first and re-resolve after.
4. **Prefer PSI when PSI can express the change.** A raw edit is the escape hatch for things PSI cannot
   say in one step (rewriting a tag-name token into a name plus attributes, deleting a template key).
   `CodeStyleManager.reformatRange` afterwards is normal and often necessary: text you splice in carries
   no formatting.

## Things that must not appear in a converted fix

| Forbidden | Instead |
|---|---|
| `WriteCommandAction`, `WriteAction`, `CommandProcessor` | nothing — the executor owns the write action |
| `preparePsiElementForWrite`, `FileModificationService` | nothing — just drop the check; the executor unlocks every changed file itself before applying (`ModCommandBatchExecutorImpl#ensureWritable`). Obtaining a *writable copy* of an element is a separate concern — `updater.getWritable` |
| `Editor`, `FileEditorManager`, `DataContext`, `CommonDataKeys` | `ActionContext` in, `ModPsiUpdater` out |
| `editor.getDocument()` | `element.getContainingFile().getFileDocument()`, or `updater.getDocument()` for the single-file case |
| `TemplateManager` + `TemplateBuilder` + editor | `updater.templateBuilder()` |
| `RefactoringUI`/`DialogWrapper`/`showAndGet` | a chooser, `ModEditOptions`, `showConflicts` — or do not convert |
| balloons / `HintManager` | `updater.message` or `updater.cancel` |

`doPostponedOperationsAndUnblockDocument` is **not** forbidden on the non-physical copy: calling it at the
very end of `psiUpdate` is useless (the framework unblocks the copy for you), but it is legitimate
in-between — e.g. to do one part of the change through PSI, unblock, then finish the rest through the
document directly.

A shared helper that legitimately needs to write to an editor can take `ModNavigator` instead of
`Editor`; callers holding a real editor pass `editor.asModNavigator()`. Real example:
`FixDocCommentAction.generateComment(PsiElement, Project, ModNavigator)` in
`community/platform/lang-impl/src/com/intellij/codeInsight/editorActions/FixDocCommentAction.java`.
