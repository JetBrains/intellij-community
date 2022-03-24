// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.find.FindBundle;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class InlineLocalHandler extends JavaInlineActionHandler {
  private static final Logger LOG = Logger.getInstance(InlineLocalHandler.class);

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiLocalVariable || element instanceof PsiPatternVariable;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression ? (PsiReferenceExpression)psiReference : null;
    doInline(project, editor, (PsiVariable)element, refExpr);
  }

  /**
   * should be called in AtomicAction
   */
  public static void invoke(@NotNull final Project project,
                            @NotNull final Editor editor,
                            @NotNull PsiLocalVariable local,
                            PsiReferenceExpression refExpr) {
    doInline(project, editor, local, refExpr);
  }

  @TestOnly
  public static void inlineVariable(@NotNull final Project project,
                                    @NotNull final Editor editor,
                                    @NotNull PsiVariable var,
                                    PsiReferenceExpression refExpr) {
    doInline(project, editor, var, refExpr);
  }

  private static void doInline(@NotNull final Project project,
                               @NotNull final Editor editor,
                               @NotNull PsiVariable var,
                               PsiReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, var)) return;
    Collection<PsiElement> allRefs = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReferencesSearch.search(var).mapping(PsiReference::getElement).findAll(),
      FindBundle.message("find.usages.progress.title"), true, project);
    if (allRefs == null) return;
    if (allRefs.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        String message = RefactoringBundle.message("variable.is.never.used", var.getName());
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(var), HelpID.INLINE_VARIABLE);
      }, ModalityState.NON_MODAL);
      return;
    }
    Runnable runnable;
    if (var instanceof PsiLocalVariable) {
      runnable = prepareLocalInline(project, editor, (PsiLocalVariable)var, refExpr, allRefs);
    }
    else {
      runnable = preparePatternInline(project, editor, (PsiPatternVariable)var, refExpr, allRefs);
    }
    if (runnable == null) return;

    CommandProcessor.getInstance()
      .executeCommand(project, () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(runnable),
                      RefactoringBundle.message("inline.command", var.getName()), null);
  }

  private static @Nullable Runnable preparePatternInline(@NotNull final Project project,
                                                         @NotNull final Editor editor,
                                                         @NotNull PsiPatternVariable pattern,
                                                         @Nullable PsiReferenceExpression refExpr,
                                                         @NotNull Collection<PsiElement> allRefs) {
    String initializerText = JavaPsiPatternUtil.getEffectiveInitializerText(pattern);
    if (initializerText == null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        String message = RefactoringBundle.message("cannot.perform.refactoring");
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(pattern), HelpID.INLINE_VARIABLE);
      }, ModalityState.NON_MODAL);
      return null;
    }
    List<PsiElement> refsToInlineList = new ArrayList<>(allRefs);
    boolean inlineAll = askInlineAll(project, pattern, refExpr, refsToInlineList);
    if (refsToInlineList.isEmpty()) return null;
    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(refsToInlineList);
    PsiExpression defToInline = JavaPsiFacade.getElementFactory(project).createExpressionFromText(initializerText, pattern);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, refsToInline, 
                                                                    EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    }

    return () -> {
      final String refactoringId = "refactoring.inline.pattern.variable";
      PsiElement scope = pattern.getDeclarationScope();
      try {
        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(refsToInline);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringStarted(refactoringId, beforeData);

        List<SmartPsiElementPointer<PsiExpression>> exprs = WriteAction.compute(
          () -> inlineOccurrences(project, pattern, defToInline, refsToInline));

        if (inlineAll && ReferencesSearch.search(pattern).findFirst() == null) {
          QuickFixFactory.getInstance().createRemoveUnusedVariableFix(pattern).invoke(project, editor, pattern.getContainingFile());
        }

        highlightOccurrences(project, editor, exprs);
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(scope);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }
    };
  }

  private static Runnable prepareLocalInline(@NotNull final Project project,
                                             @NotNull final Editor editor,
                                             @NotNull PsiLocalVariable local,
                                             @Nullable PsiReferenceExpression refExpr,
                                             @NotNull Collection<PsiElement> allRefs) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();

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
    final PsiCodeBlock containerBlock = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
    if (containerBlock == null) {
      final String message = RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("inline.local.variable.declared.outside.cannot.refactor.message"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
      return null;
    }

    final PsiExpression defToInline;
    try {
      defToInline = getDefToInline(local, innerClassesWithUsages.isEmpty() ? refExpr : innerClassesWithUsages.get(0), containerBlock, true);
      if (defToInline == null) {
        final String key = refExpr == null ? "variable.has.no.initializer" : "variable.has.no.dominating.definition";
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
        return null;
      }
    }
    catch (RuntimeException e) {
      processWrappedAnalysisCanceledException(project, editor, e);
      return null;
    }

    List<PsiElement> refsToInlineList = new ArrayList<>();
    try {
      Collections.addAll(refsToInlineList, DefUseUtil.getRefs(containerBlock, local, defToInline));
    }
    catch (RuntimeException e) {
      processWrappedAnalysisCanceledException(project, editor, e);
      return null;
    }
    for (PsiElement innerClassUsage : innerClassUsages) {
      if (!refsToInlineList.contains(innerClassUsage)) {
        refsToInlineList.add(innerClassUsage);
      }
    }
    if (refsToInlineList.isEmpty()) {
      String message = JavaRefactoringBundle.message("variable.is.never.used.before.modification", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
      return null;
    }

    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    InlineUtil.checkChangedBeforeLastAccessConflicts(conflicts, defToInline, local);

    if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return null;

    boolean inlineAll = askInlineAll(project, local, refExpr, refsToInlineList);
    if (refsToInlineList.isEmpty()) return null;

    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(refsToInlineList);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // TODO : check if initializer uses fieldNames that possibly will be hidden by other
      //       locals with the same names after inlining
      highlightManager.addOccurrenceHighlights(
        editor,
        refsToInline,
        EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null
      );
    }

    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && ArrayUtil.find(refsToInline, refExpr) < 0) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, refExpr);
      LOG.assertTrue(defs.length > 0);
      highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return null;
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
        String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
        return null;
      }
      if (tryStatement != null && !PsiTreeUtil.isAncestor(tryStatement, ref, false)) {
        CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.local.unable.try.catch.warning.message"), getRefactoringName(local),
                                            HelpID.INLINE_VARIABLE);
        return null;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, ref);
      boolean isSameDefinition = true;
      for (PsiElement def : defs) {
        isSameDefinition &= isSameDefinition(def, defToInline);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        String message =
          RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return null;
      }
    }

    final PsiElement writeAccess = checkRefsInAugmentedAssignmentOrUnaryModified(refsToInline, defToInline);
    if (writeAccess != null) {
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{writeAccess}, 
                                                                    EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(local), HelpID.INLINE_VARIABLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return null;
    }

    if (Arrays.stream(refsToInline).anyMatch(ref -> ref.getParent() instanceof PsiResourceExpression)) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("inline.local.used.as.resource.cannot.refactor.message")),
                       getRefactoringName(local), HelpID.INLINE_VARIABLE);
      return null;
    }

    return () -> {
      final String refactoringId = "refactoring.inline.local.variable";
      try {

        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(refsToInline);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringStarted(refactoringId, beforeData);

        List<SmartPsiElementPointer<PsiExpression>> exprs = WriteAction.compute(() -> {
          List<SmartPsiElementPointer<PsiExpression>> pointers = inlineOccurrences(project, local, defToInline, refsToInline);

          if (inlineAll) {
            if (!isInliningVariableInitializer(defToInline)) {
              deleteInitializer(defToInline);
            }
            else {
              defToInline.delete();
            }
          }
          return pointers;
        });

        if (inlineAll && ReferencesSearch.search(local).findFirst() == null) {
          QuickFixFactory.getInstance().createRemoveUnusedVariableFix(local).invoke(project, editor, local.getContainingFile());
        }

        highlightOccurrences(project, editor, exprs);

        WriteAction.run(() -> {
          for (SmartPsiElementPointer<PsiExpression> expr : exprs) {
            CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expr.getElement());
          }
        });
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(containingClass);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }
    };
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

  static boolean askInlineAll(@NotNull Project project,
                              @NotNull PsiVariable variable,
                              @Nullable PsiReferenceExpression refExpr,
                              @NotNull List<PsiElement> refsToInlineList) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    int occurrencesCount = refsToInlineList.size();
    if (refExpr != null && EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog()) {
      final InlineLocalDialog inlineLocalDialog = new InlineLocalDialog(project, variable, refExpr, occurrencesCount);
      if (!inlineLocalDialog.showAndGet()) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        refsToInlineList.clear();
        return false;
      }
      else if (inlineLocalDialog.isInlineThis()) {
        refsToInlineList.clear();
        refsToInlineList.add(refExpr);
        return false;
      }
    }
    return true;
  }

  static void highlightOccurrences(@NotNull Project project,
                                   @Nullable Editor editor,
                                   @NotNull List<SmartPsiElementPointer<PsiExpression>> exprs) {
    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      PsiExpression[] occurrences = StreamEx.of(exprs).map(SmartPsiElementPointer::getElement).nonNull().toArray(PsiExpression.EMPTY_ARRAY);
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, occurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      if (exprs.size() > 1) {
        Shortcut shortcut = KeymapUtil.getPrimaryShortcut("FindNext");
        String message;
        if (shortcut != null) {
          message =
            JavaBundle.message("hint.text.press.to.go.through.inlined.occurrences", KeymapUtil.getShortcutText(shortcut), exprs.size());
        }
        else {
          message = JavaBundle.message("hint.text.occurrences.were.inlined", exprs.size());
        }
        HintManagerImpl.getInstanceImpl().showInformationHint(editor, message, HintManager.UNDER);
      }
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    }
  }
  
  private static void processWrappedAnalysisCanceledException(@NotNull Project project,
                                                              Editor editor,
                                                              RuntimeException e) {
    Throwable cause = e.getCause();
    if (cause instanceof AnalysisCanceledException) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          RefactoringBundle.getCannotRefactorMessage(
                                            JavaRefactoringBundle.message("extract.method.control.flow.analysis.failed")),
                                          RefactoringBundle.message("inline.variable.title"), HelpID.INLINE_VARIABLE);
      return;
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

      if (def instanceof PsiReferenceExpression && def.getParent() instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)def.getParent();
        if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) return null;
        final PsiExpression rExpr = assignmentExpression.getRExpression();
        if (rExpr != null) return rExpr;
      }
    }
    return local.getInitializer();
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return getRefactoringName(element);
  }

  private static @NlsContexts.DialogTitle String getRefactoringName(PsiElement variable) {
    return variable instanceof PsiPatternVariable
                     ? JavaRefactoringBundle.message("inline.pattern.variable.title")
                     : RefactoringBundle.message("inline.variable.title");
  }
}
