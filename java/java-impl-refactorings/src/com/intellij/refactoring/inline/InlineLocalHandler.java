// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InlineLocalHandler extends JavaInlineActionHandler {
  private enum InlineMode {
    CHECK_CONFLICTS,
    ASK,
    HIGHLIGHT_CONFLICTS,
    INLINE_ONE,
    INLINE_ALL_AND_DELETE
  }

  private static final Logger LOG = Logger.getInstance(InlineLocalHandler.class);

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiLocalVariable || element instanceof PsiPatternVariable;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final String refactoringId = getRefactoringId(element);
    RefactoringEventData beforeData = new RefactoringEventData();
    PsiElement scope = getScope(element);
    beforeData.addElement(element);
    beforeData.addElement(scope);
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringStarted(refactoringId, beforeData);
    try {
      ActionContext context =
        ActionContext.from(editor, element.getContainingFile()).withElement(element);
      String name = getActionName(element);
      ModCommand command = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        return ReadAction.nonBlocking(() -> perform(context)).executeSynchronously();
      }, name, true, context.project());
      if (command == null) return;
      CommandProcessor.getInstance().executeCommand(
        context.project(), () -> ModCommandExecutor.getInstance().executeInteractively(context, command, editor), name, null);
    }
    finally {
      final RefactoringEventData afterData = new RefactoringEventData();
      afterData.addElement(scope);
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
    }
  }

  @NotNull
  private static String getRefactoringId(@NotNull PsiElement element) {
    return element instanceof PsiPatternVariable ? "refactoring.inline.pattern.variable" : "refactoring.inline.local.variable";
  }

  private static PsiElement getScope(PsiElement element) {
    return PsiUtil.getVariableCodeBlock((PsiVariable)element, null);
  }

  private static @NotNull ModCommand perform(ActionContext context) {
    PsiElement element = context.findLeaf();
    if (!(element instanceof PsiIdentifier)) {
      element = context.findLeafOnTheLeft();
    }
    final PsiReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    return doInline(context, (PsiVariable)Objects.requireNonNull(context.element()), refExpr, InlineMode.CHECK_CONFLICTS);
  }

  private static ModCommand doInline(@NotNull ActionContext context,
                                     @NotNull PsiVariable var,
                                     PsiReferenceExpression refExpr,
                                     @NotNull InlineMode mode) {
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    List<PsiReferenceExpression> allRefs =
      mode == InlineMode.INLINE_ONE || block == null ? List.of(refExpr) :
      VariableAccessUtils.getVariableReferences(var, block);
    if (allRefs.isEmpty()) {
      return ModCommand.error(RefactoringBundle.message("variable.is.never.used", var.getName()));
    }
    if (var instanceof PsiLocalVariable local) {
      return inlineLocal(context, local, refExpr, allRefs, mode);
    }
    return inlinePattern(context, (PsiPatternVariable)var, refExpr, allRefs, mode);
  }

  private static @NotNull ModCommand inlinePattern(@NotNull ActionContext context,
                                                   @NotNull PsiPatternVariable pattern,
                                                   @Nullable PsiReferenceExpression refExpr,
                                                   @NotNull List<PsiReferenceExpression> allRefs,
                                                   @NotNull InlineMode mode) {
    String initializerText = JavaPsiPatternUtil.getEffectiveInitializerText(pattern);
    if (initializerText == null) {
      return ModCommand.error(JavaRefactoringBundle.message("tooltip.cannot.inline.pattern.variable"));
    }
    Project project = context.project();
    if (mode == InlineMode.CHECK_CONFLICTS || mode == InlineMode.ASK) {
      if (refExpr == null || allRefs.size() == 1 || !EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog()) {
        mode = InlineMode.INLINE_ALL_AND_DELETE;
      } else {
        return createChooser(pattern, refExpr, allRefs);
      }
    }
    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(allRefs);
    PsiExpression defToInline = JavaPsiFacade.getElementFactory(project).createExpressionFromText(initializerText, pattern);

    boolean inlineAll = mode == InlineMode.INLINE_ALL_AND_DELETE;
    return ModCommand.psiUpdate(context, updater -> {
      PsiPatternVariable writablePattern = updater.getWritable(pattern);
      List<SmartPsiElementPointer<PsiExpression>> pointers =
        inlineOccurrences(project, writablePattern, defToInline,
                          ContainerUtil.map2Array(refsToInline, PsiElement.EMPTY_ARRAY, updater::getWritable));
      if (inlineAll) {
        writablePattern.delete();
      }
      highlightOccurrences(updater, pointers);
    });
  }

  @NotNull
  private static ModChooseAction createChooser(@NotNull PsiVariable variable,
                                               @Nullable PsiReferenceExpression refExpr,
                                               @NotNull List<? extends PsiElement> allRefs) {
    List<ModCommandAction> actions = List.of(
      new InlineLocalStep(variable, refExpr, InlineMode.INLINE_ONE, allRefs),
      new InlineLocalStep(variable, refExpr, InlineMode.INLINE_ALL_AND_DELETE, allRefs));
    return new ModChooseAction(getRefactoringName(variable), actions);
  }


  private static ModCommand createConflictChooser(PsiLocalVariable variable,
                                                  PsiReferenceExpression refExpr,
                                                  Map<PsiElement, PsiVariable> conflicts) {
    List<ModCommandAction> actions = List.of(
      new InlineLocalStep(variable, refExpr, InlineMode.HIGHLIGHT_CONFLICTS, conflicts.keySet()),
      new InlineLocalStep(variable, refExpr, InlineMode.ASK, List.of()));
    return new ModChooseAction(JavaRefactoringBundle.message("inline.warning.variables.used.in.initializer.are.updated"), actions);
  }


  private static @NotNull ModCommand inlineLocal(@NotNull ActionContext context,
                                                 @NotNull PsiLocalVariable local,
                                                 @Nullable PsiReferenceExpression refExpr,
                                                 @NotNull List<PsiReferenceExpression> allRefs,
                                                 @NotNull InlineMode mode) {
    final String localName = local.getName();

    InnerClassUsages innerClassUses = InnerClassUsages.getUsages(local, allRefs);
    final PsiCodeBlock containerBlock = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
    if (containerBlock == null) {
      return ModCommand.error(JavaRefactoringBundle.message("inline.local.variable.declared.outside.cannot.refactor.message"));
    }

    final PsiExpression defToInline;
    try {
      List<PsiElement> innerClassesWithUsages = innerClassUses.innerClassesWithUsages();
      defToInline = getDefToInline(local, innerClassesWithUsages.isEmpty() ? refExpr : innerClassesWithUsages.get(0), containerBlock, true);
      if (defToInline == null) {
        final String key = refExpr == null ? "variable.has.no.initializer" : "variable.has.no.dominating.definition";
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
        return ModCommand.error(message);
      }
    }
    catch (RuntimeException e) {
      return processWrappedAnalysisCanceledException(e);
    }

    List<PsiElement> refsToInlineList = new ArrayList<>();
    if (mode == InlineMode.INLINE_ONE) {
      refsToInlineList.add(refExpr);
    } else {
      try {
        Collections.addAll(refsToInlineList, DefUseUtil.getRefs(containerBlock, local, defToInline));
      }
      catch (RuntimeException e) {
        return processWrappedAnalysisCanceledException(e);
      }
      for (PsiElement innerClassUsage : innerClassUses.innerClassUsages()) {
        if (!refsToInlineList.contains(innerClassUsage)) {
          refsToInlineList.add(innerClassUsage);
        }
      }
    }
    if (refsToInlineList.isEmpty()) {
      return ModCommand.error(JavaRefactoringBundle.message("variable.is.never.used.before.modification", localName));
    }

    if (mode == InlineMode.CHECK_CONFLICTS) {
      Map<PsiElement, PsiVariable> conflicts = InlineUtil.getChangedBeforeLastAccessMap(defToInline, local);
      if (conflicts.isEmpty()) {
        mode = InlineMode.ASK;
      } else {
        return createConflictChooser(local, refExpr, conflicts);
      }
    }

    if (mode == InlineMode.ASK && refExpr != null && refsToInlineList.size() > 1 &&
        EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog()) {
      return createChooser(local, refExpr, refsToInlineList);
    }

    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(refsToInlineList);

    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && ArrayUtil.find(refsToInline, refExpr) < 0) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, refExpr);
      LOG.assertTrue(defs.length > 0);
      return ModCommand.highlight(defs).andThen(
        ModCommand.error(
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", localName))));
    }

    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(defToInline, PsiTryStatement.class);
    if (tryStatement != null) {
      if (ExceptionUtil.getThrownExceptions(defToInline).isEmpty()) {
        tryStatement = null;
      }
    }
    PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        return ModCommand.error(RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName));
      }
      if (tryStatement != null && !PsiTreeUtil.isAncestor(tryStatement, ref, false)) {
        return ModCommand.error(JavaRefactoringBundle.message("inline.local.unable.try.catch.warning.message"));
      }
    }

    for (final PsiElement ref : refsToInline) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, ref);
      boolean isSameDefinition = true;
      for (PsiElement def : defs) {
        isSameDefinition &= isSameDefinition(def, defToInline);
      }
      if (!isSameDefinition) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(
            RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        return ModCommand.highlight(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, defs)
          .andThen(ModCommand.highlight(ref))
          .andThen(ModCommand.error(message));
      }
    }

    final PsiElement writeAccess = checkRefsInAugmentedAssignmentOrUnaryModified(refsToInline, defToInline);
    if (writeAccess != null) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", localName));
      return ModCommand.highlight(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, writeAccess)
        .andThen(ModCommand.error(message));
    }

    if (ContainerUtil.exists(refsToInline, ref -> ref.getParent() instanceof PsiResourceExpression)) {
      return ModCommand.error(
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("inline.local.used.as.resource.cannot.refactor.message")));
    }
    Project project = context.project();

    boolean inlineAll = mode != InlineMode.INLINE_ONE;
    return ModCommand.psiUpdate(context, updater -> {
      PsiExpression writableDef = updater.getWritable(defToInline);
      PsiLocalVariable writableLocal = updater.getWritable(local);
      PsiElement[] writableRefs = ContainerUtil.map2Array(refsToInline, PsiElement.EMPTY_ARRAY, updater::getWritable);
      List<SmartPsiElementPointer<PsiExpression>> pointers = inlineOccurrences(project, writableLocal, writableDef, writableRefs);

      if (inlineAll) {
        if (!isInliningVariableInitializer(writableDef)) {
          deleteInitializer(writableDef);
        }
        else {
          writableDef.delete();
        }
      }

      if (inlineAll &&
          !VariableAccessUtils.variableIsUsed(writableLocal, PsiUtil.getVariableCodeBlock(writableLocal, null))) {
        writableLocal.normalizeDeclaration();
        new CommentTracker().deleteAndRestoreComments(writableLocal);
      }

      highlightOccurrences(updater, pointers);

      for (SmartPsiElementPointer<PsiExpression> expr : pointers) {
        CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expr.getElement());
      }
    });
  }

  private record InnerClassUsages(List<PsiElement> innerClassesWithUsages, List<PsiElement> innerClassUsages) {
    @NotNull
    private static InnerClassUsages getUsages(@NotNull PsiLocalVariable local, @NotNull List<PsiReferenceExpression> allRefs) {
      final List<PsiElement> innerClassesWithUsages = new ArrayList<>();
      final List<PsiElement> innerClassUsages = new ArrayList<>();
      final PsiElement containingClass = LambdaUtil.getContainingClassOrLambda(local);
      for (PsiElement element : allRefs) {
        PsiElement innerClass = element;
        while (innerClass != null) {
          final PsiElement parentPsiClass = LambdaUtil.getContainingClassOrLambda(innerClass.getParent());
          if (parentPsiClass == containingClass) {
            if (innerClass != element) {
              innerClassesWithUsages.add(innerClass);
              innerClassUsages.add(element);
            }
            break;
          }
          innerClass = parentPsiClass;
        }
      }
      return new InnerClassUsages(innerClassesWithUsages, innerClassUsages);
    }
  }

  @NotNull
  static List<SmartPsiElementPointer<PsiExpression>> inlineOccurrences(@NotNull Project project,
                                                                       @NotNull PsiVariable local,
                                                                       PsiExpression defToInline,
                                                                       PsiElement[] refsToInline) {
    List<SmartPsiElementPointer<PsiExpression>> pointers = new ArrayList<>();
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    for (PsiElement element : refsToInline) {
      PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)element;
      pointers.add(pointerManager.createSmartPsiElementPointer(InlineUtil.inlineVariable(local, defToInline, refElement)));
    }
    return pointers;
  }

  private static void highlightOccurrences(ModPsiUpdater updater, List<SmartPsiElementPointer<PsiExpression>> pointers) {
    for (SmartPsiElementPointer<PsiExpression> pointer : pointers) {
      PsiExpression expression = pointer.getElement();
      if (expression != null) {
        updater.highlight(expression);
      }
    }
    if (pointers.size() > 1) {
      Shortcut shortcut = KeymapUtil.getPrimaryShortcut("FindNext");
      String message;
      if (shortcut != null) {
        message =
          JavaBundle.message("hint.text.press.to.go.through.inlined.occurrences", KeymapUtil.getShortcutText(shortcut), pointers.size());
      }
      else {
        message = JavaBundle.message("hint.text.occurrences.were.inlined", pointers.size());
      }
      updater.message(message);
    }
  }

  private static @NotNull ModCommand processWrappedAnalysisCanceledException(@NotNull RuntimeException e) {
    Throwable cause = e.getCause();
    if (cause instanceof AnalysisCanceledException) {
      return ModCommand.error(
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("extract.method.control.flow.analysis.failed")));
    }
    throw e;
  }

  private static void deleteInitializer(@NotNull PsiExpression defToInline) {
    PsiElement parent = defToInline.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
      if (!(gParent instanceof PsiExpressionStatement)) {
        parent.replace(defToInline);
        return;
      }
    }

    parent.delete();
  }

  @Nullable
  static PsiElement checkRefsInAugmentedAssignmentOrUnaryModified(final PsiElement[] refsToInline, PsiElement defToInline) {
    for (PsiElement element : refsToInline) {

      PsiElement parent = element.getParent();
      if (parent instanceof PsiArrayAccessExpression) {
        if (((PsiArrayAccessExpression)parent).getIndexExpression() == element) continue;
        if (defToInline instanceof PsiExpression && !(defToInline instanceof PsiNewExpression)) continue;
        element = parent;
      }

      if (RefactoringUtil.isAssignmentLHS(element)) {
        return element;
      }
    }
    return null;
  }

  private static boolean isSameDefinition(final PsiElement def, final PsiExpression defToInline) {
    if (def instanceof PsiLocalVariable) return defToInline.equals(((PsiLocalVariable)def).getInitializer());
    final PsiElement parent = def.getParent();
    return parent instanceof PsiAssignmentExpression && defToInline.equals(((PsiAssignmentExpression)parent).getRExpression());
  }

  private static boolean isInliningVariableInitializer(final PsiExpression defToInline) {
    return defToInline.getParent() instanceof PsiVariable;
  }

  @Nullable
  static PsiExpression getDefToInline(final PsiVariable local,
                                      final PsiElement refExpr,
                                      @NotNull PsiCodeBlock block,
                                      final boolean rethrow) {
    if (refExpr != null) {
      PsiElement def;
      if (refExpr instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)refExpr)) {
        def = refExpr;
      }
      else {
        final PsiElement[] defs = DefUseUtil.getDefs(block, local, refExpr, rethrow);
        if (defs.length == 1) {
          def = defs[0];
        }
        else {
          return null;
        }
      }

      if (def instanceof PsiReferenceExpression && def.getParent() instanceof PsiAssignmentExpression assignmentExpression) {
        if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) return null;
        final PsiExpression rExpr = assignmentExpression.getRExpression();
        if (rExpr != null) return rExpr;
      }
    }
    return local.getInitializer();
  }

  @NotNull
  @Override
  public String getActionName(PsiElement element) {
    return getRefactoringName(element);
  }

  @NotNull
  private static @NlsContexts.DialogTitle String getRefactoringName(PsiElement variable) {
    return variable instanceof PsiPatternVariable
           ? JavaRefactoringBundle.message("inline.pattern.variable.title")
           : RefactoringBundle.message("inline.variable.title");
  }

  private static class InlineLocalStep implements ModCommandAction {
    private final @NotNull PsiVariable myPattern;
    private final @Nullable PsiReferenceExpression myRefExpr;
    private final @NotNull InlineMode myMode;
    private final @NotNull Collection<? extends PsiElement> myAllRefs;

    private InlineLocalStep(@NotNull PsiVariable pattern,
                            @Nullable PsiReferenceExpression refExpr,
                            @NotNull InlineMode mode,
                            @NotNull Collection<? extends PsiElement> allRefs) {
      myPattern = pattern;
      myRefExpr = refExpr;
      myMode = mode;
      myAllRefs = allRefs;
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      if (myMode == InlineMode.HIGHLIGHT_CONFLICTS) {
        return ModCommand.highlight(myAllRefs.toArray(PsiElement.EMPTY_ARRAY)).andThen(
          myAllRefs.stream().findFirst().map(ModCommand::select).orElse(ModCommand.nop()));
      }
      return doInline(context, myPattern, myRefExpr, myMode);
    }

    @Override
    public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
      return Presentation.of(getFamilyName())
        .withHighlighting(
          myMode == InlineMode.INLINE_ONE ? new TextRange[]{Objects.requireNonNull(myRefExpr).getTextRange()} :
          ContainerUtil.map2Array(myAllRefs, TextRange.EMPTY_ARRAY, PsiElement::getTextRange));
    }

    @Override
    public @NotNull String getFamilyName() {
      return switch (myMode) {
        case HIGHLIGHT_CONFLICTS -> JavaRefactoringBundle.message("inline.popup.highlight", myAllRefs.size());
        case ASK -> JavaRefactoringBundle.message("inline.popup.ignore.conflicts");
        case INLINE_ONE -> RefactoringBundle.message("inline.popup.this.only");
        case INLINE_ALL_AND_DELETE -> RefactoringBundle.message("inline.popup.all", myAllRefs.size());
        default -> throw new IllegalStateException("Unexpected value: " + myMode);
      };
    }
  }
}
