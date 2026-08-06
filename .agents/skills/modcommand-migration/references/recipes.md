# Conversion recipes

Each recipe shows the legacy shape and its ModCommand equivalent, and points at a converted file in this
repo to copy from.

## 1. Plain single-file fix

The most common case, and genuinely a four-line change.

```java
// before
public class AddArgumentFix implements LocalQuickFix {
  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    ...
  }
}

// after
public class AddArgumentFix extends PsiUpdateModCommandQuickFix {
  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    ...
  }
}
```

`getName()`/`getFamilyName()` stay exactly as they were. Marker interfaces still work.

Reference: `AddArgumentFix` (`community/java/java-impl/src/com/siyeh/ig/fixes/AddArgumentFix.java`) —
note that non-element state (here two `String` fields) is still fine in a field, and no
`@SafeFieldForPreview` is needed.

## 2. Fix holding an element pointer

A `LocalQuickFix` whose constructor takes a `PsiElement` becomes a `PsiUpdateModCommandAction<E>`, which
binds to that element for you: it is passed back already validity- and writability-checked.

```java
private static class InsertParagraphTagFix extends PsiUpdateModCommandAction<PsiElement> {
  protected InsertParagraphTagFix(@NotNull PsiElement element) { super(element); }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) { ... }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Presentation.of(JavaBundle.message("inspection.javadoc.blank.lines.fix.name"));
  }

  @Override
  public @NotNull String getFamilyName() { return JavaBundle.message("...family.name"); }
}
```

Register it with the builder API, which accepts a `ModCommandAction` directly:

```java
holder.problem(token, message).fix(new InsertParagraphTagFix(token)).register();
```

`ProblemsHolder.problem(...)` also offers `.range(...)`, `.highlight(...)`, `.maybeFix(...)`, and must
end with `.register()`. Where you need a `LocalQuickFix` value instead, use
`LocalQuickFix.from(action)`.

Reference:
`JavadocBlankLinesInspection` (`community/java/java-impl/src/com/intellij/codeInspection/javaDoc/JavadocBlankLinesInspection.java`)
(fix class near the bottom of the file, registration in the visitor).

If the presentation text does not depend on context, you can drop `getPresentation` entirely — the
default is `Presentation.of(getFamilyName())`.

## 3. A fix that used the `Editor`

```java
// before
Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
if (editor == null) return;
editor.getCaretModel().moveToOffset(newElement.getTextRange().getStartOffset());
editor.getSelectionModel().setSelection(start, end);

// after — no null check, no editor
updater.moveCaretTo(newElement.getTextRange().getStartOffset());
updater.select(newElement);
```

Same for highlighting (`updater.highlight`). Do **not** guard these with `isOnTheFly` — batch execution
drops them for you.

## 4. Live template or rename UI

```java
// before: commit document, get editor, TemplateBuilderImpl, TemplateManager.startTemplate...
// after:
variable.setInitializer(initializer);
ModTemplateBuilder builder = updater.templateBuilder();
builder.field(Objects.requireNonNull(variable.getInitializer()),
              new ConstantNode(...).withLookupItems(suggestedInitializers));
```

References:
`AddVariableInitializerFix` (`community/java/java-impl/src/com/intellij/codeInsight/daemon/impl/quickfix/AddVariableInitializerFix.java`)
(single field with lookup items),
`AddMissingPropertyFix` (`community/json/backend/src/com/jetbrains/jsonSchema/impl/fixes/AddMissingPropertyFix.java`)
(`moveCaretTo` + `field(element, range, name, expr)` + `finishAt`).

For "create it, then let the user name it", use `updater.rename(element, suggestedNames)` instead of
building a rename template by hand.

## 5. Fix that writes to several files

```java
// before
if (!FileModificationService.getInstance().preparePsiElementForWrite(other)) return;
WriteCommandAction.runWriteCommandAction(project, () -> { ...edit this file and other... });

// after — get every writable copy first, then edit
PsiMethod writableCtor = updater.getWritable(constructor);   // other file
PsiField  writableField = element;                           // already writable
...edit both...
```

**All `getWritable` calls must precede all writes** — it throws `IllegalStateException` once that file's
copy has been modified. The executor produces one composite command covering every touched file.

## 6. Confirmation dialog or conflicts

A `ConflictsDialog`, or a "this will break subclasses, continue?" message box, becomes
`ModCommand.showConflicts` composed *before* the edit. Note the preview guard: preview skips
`ModShowConflicts` entirely, so computing conflicts there is wasted work.

```java
@Override
public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
  ...
  final List<String> conflictMessages = new ArrayList<>();
  if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
    ClassInheritorsSearch.search(containingClass).forEach(aClass -> {
      conflictMessages.add(describeConflict(containingClass, aClass));
      return true;
    });
  }
  Map<PsiElement, ModShowConflicts.Conflict> conflicts = conflictMessages.isEmpty()
    ? Map.of()
    : Map.of(containingClass, new ModShowConflicts.Conflict(conflictMessages));
  return showConflicts(conflicts)
    .andThen(psiUpdate(modifierList, MakeClassFinalFix::doMakeFinal));
}
```

Reference: `MakeClassFinalFix` (`community/java/java-impl/src/com/siyeh/ig/fixes/MakeClassFinalFix.java`) for
the preview guard and command composition pattern. `showConflicts` returns `nop()` for an empty map, so
build an empty map when there are no messages; a non-empty map containing an empty `Conflict` is still a
`ModShowConflicts` and reports `CONFLICTS` in batch.
`updater.showConflicts(...)` is the equivalent from inside a `psiUpdate` lambda.

## 7. A popup that chooses an element or a variant

`ModCommand.chooseAction` + `ModCommand.psiUpdateStep`. Each option is a child `ModCommandAction`, gets
its own preview, and can highlight what it will affect via `Presentation.withHighlighting` — which
`psiUpdateStep`'s `range` argument sets up for you.

```java
List<ModCommandAction> actions = ContainerUtil.map(declarations, var -> ModCommand.psiUpdateStep(
  var,                                          // element the step is anchored to
  JavaBundle.message("intention.rename.underscore.name", ...),   // option title
  (v, updater) -> { ... },                      // what the option does
  v -> v.getTextRange()));                      // range to highlight while hovering
return ModCommand.chooseAction(JavaBundle.message("...popup.title"), actions);
```

Reference:
`RenameUnderscoreFix` (`community/java/java-impl/src/com/intellij/codeInsight/daemon/impl/quickfix/RenameUnderscoreFix.java`)
— also shows `updater.getWritable` for a second element inside a step, and `updater.rename`.

Two rules: **put the safest option first** (batch and preview auto-select it), and do not special-case a
single option (it is executed without showing UI).

## 8. `MemberChooser`

```java
List<PsiMethodMember> allMembers = ContainerUtil.map(ctors, PsiMethodMember::new);
if (ctors.size() == 1) return getFinalCommand(field, allMembers);   // skip the UI
return ModCommand.chooseMultipleMembers(
  QuickFixBundle.message("...choose.dialog.title"), allMembers,
  chosenMembers -> getFinalCommand(field, chosenMembers));
```

The callback runs in a read action and returns the next command; in it, map the chosen
`OptElement`s back to PSI with `updater.getWritable`.

Reference:
`InitializeFinalFieldInConstructorFix` (`community/java/java-impl/src/com/intellij/codeInsight/daemon/impl/quickfix/InitializeFinalFieldInConstructorFix.java`).

## 9. `BatchQuickFix`

Extend `ModCommandBatchQuickFix` and implement `perform(Project, List<ProblemDescriptor>)`, returning one
command for all descriptors. The single-descriptor `perform` and `applyFix` are handled for you. Build it
as one `psiUpdate` over the shared context and `getWritable` each descriptor's element — see the batch
branch of
`AddMissingPropertyFix` (`community/json/backend/src/com/jetbrains/jsonSchema/impl/fixes/AddMissingPropertyFix.java`).

## 10. Not convertible

Keep the `LocalQuickFix`, and optionally implement
`LocalQuickFixWithModCommandFallback` (`community/platform/analysis-api/src/com/intellij/modcommand/LocalQuickFixWithModCommandFallback.java`)
so headless clients get a reduced version:

```java
public class MyFix implements LocalQuickFixWithModCommandFallback {
  @Override
  public @NotNull ModCommandAction getFallbackModCommandAction() {
    return new MySimplifiedAction(myElement);
  }
}
```

The fallback is allowed to be less capable — that is its purpose. Do not fake the unsupported part.
