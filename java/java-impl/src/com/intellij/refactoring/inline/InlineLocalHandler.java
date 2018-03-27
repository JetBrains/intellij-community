/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InlineLocalHandler extends JavaInlineActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineLocalHandler");

  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.variable.title");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiLocalVariable;
  }

  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression ? ((PsiReferenceExpression)psiReference) : null;
    invoke(project, editor, (PsiLocalVariable) element, refExpr);
  }

  /**
   * should be called in AtomicAction
   */
  public static void invoke(@NotNull final Project project, final Editor editor, final PsiLocalVariable local, PsiReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();

    final List<PsiElement> innerClassesWithUsages = Collections.synchronizedList(new ArrayList<PsiElement>());
    final List<PsiElement> innerClassUsages = Collections.synchronizedList(new ArrayList<PsiElement>());
    final PsiElement containingClass = PsiTreeUtil.getParentOfType(local, PsiClass.class, PsiLambdaExpression.class);
    final Query<PsiReference> query = ReferencesSearch.search(local);
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      if (query.findFirst() == null){
        LOG.assertTrue(refExpr == null);
        ApplicationManager.getApplication().invokeLater(() -> {
          String message = RefactoringBundle.message("variable.is.never.used", localName);
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        }, ModalityState.NON_MODAL);
        return;
      }
      query.forEach(psiReference -> {
        final PsiElement element = psiReference.getElement();
        PsiElement innerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, PsiLambdaExpression.class);
        while (innerClass != containingClass && innerClass != null) {
          final PsiElement parentPsiClass = PsiTreeUtil.getParentOfType(innerClass.getParent(), PsiClass.class, PsiLambdaExpression.class);
          if (parentPsiClass == containingClass) {
            if (innerClass instanceof PsiLambdaExpression) {
              if (PsiTreeUtil.isAncestor(innerClass, local, false)) {
                innerClassesWithUsages.add(element);
                innerClass = parentPsiClass;
                continue;
              }
            }
            innerClassesWithUsages.add(innerClass);
            innerClassUsages.add(element);
          }
          innerClass = parentPsiClass;
        }
        return true;
      });

    }, "Find Usages", true, project)) {
      return;
    }
    final PsiCodeBlock containerBlock = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
    if (containerBlock == null) {
      final String message = RefactoringBundle.getCannotRefactorMessage("Variable is declared outside a code block");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final PsiExpression defToInline;
    try {
      defToInline = getDefToInline(local, innerClassesWithUsages.isEmpty() ? refExpr : innerClassesWithUsages.get(0), containerBlock, true);
      if (defToInline == null){
        final String key = refExpr == null ? "variable.has.no.initializer" : "variable.has.no.dominating.definition";
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof AnalysisCanceledException) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("extract.method.control.flow.analysis.failed")),
                                            REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
      throw e;
    }

    List<PsiElement> refsToInlineList = new ArrayList<>();
    Collections.addAll(refsToInlineList, DefUseUtil.getRefs(containerBlock, local, defToInline));
    for (PsiElement innerClassUsage : innerClassUsages) {
      if (!refsToInlineList.contains(innerClassUsage)) {
        refsToInlineList.add(innerClassUsage);
      }
    }
    if (refsToInlineList.size() == 0) {
      String message = RefactoringBundle.message("variable.is.never.used.before.modification", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final Ref<Boolean> inlineAll = new Ref<>(true);
    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      int occurrencesCount = refsToInlineList.size();
      if (refExpr != null && occurrencesCount > 1  || EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog()) {
        final InlineLocalDialog inlineLocalDialog = new InlineLocalDialog(project, local, refExpr, occurrencesCount);
        if (!inlineLocalDialog.showAndGet()) {
          WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
          return;
        }

        if (refExpr != null && inlineLocalDialog.isInlineThis()) {
          refsToInlineList = Collections.singletonList(refExpr);
          inlineAll.set(false);
        }
      }
    }

    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(refsToInlineList);

    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      // TODO : check if initializer uses fieldNames that possibly will be hidden by other
      // locals with the same names after inlining
      highlightManager.addOccurrenceHighlights(
        editor,
        refsToInline,
        attributes, true, null
      );
    }

    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && ArrayUtil.find(refsToInline, refExpr) < 0) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, refExpr);
      LOG.assertTrue(defs.length > 0);
      highlightManager.addOccurrenceHighlights(editor, defs, attributes, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return;
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
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
      if (tryStatement != null && !PsiTreeUtil.isAncestor(tryStatement, ref, false)) {
        CommonRefactoringUtil.showErrorHint(project, editor, "Unable to inline outside try/catch statement", REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, ref);
      boolean isSameDefinition = true;
      for (PsiElement def : defs) {
        isSameDefinition &= isSameDefinition(def, defToInline);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, writeAttributes, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, attributes, true, null);
        String message =
          RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final PsiElement writeAccess = checkRefsInAugmentedAssignmentOrUnaryModified(refsToInline, defToInline);
    if (writeAccess != null) {
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{writeAccess}, writeAttributes, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return;
    }

    if (Arrays.stream(refsToInline).anyMatch(ref -> ref.getParent() instanceof PsiResourceExpression)) {
      CommonRefactoringUtil.showErrorHint(project, editor,  RefactoringBundle.getCannotRefactorMessage("Variable is used as resource reference"), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final Runnable runnable = () -> {
      final String refactoringId = "refactoring.inline.local.variable";
      try{
        SmartPsiElementPointer<PsiExpression>[] exprs = new SmartPsiElementPointer[refsToInline.length];

        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(refsToInline);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(refactoringId, beforeData);

        WriteAction.run(() -> {
          final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
          for(int idx = 0; idx < refsToInline.length; idx++){
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)refsToInline[idx];
            exprs[idx] = pointerManager.createSmartPsiElementPointer(InlineUtil.inlineVariable(local, defToInline, refElement));
          }

          if (inlineAll.get()) {
            if (!isInliningVariableInitializer(defToInline)) {
              deleteInitializer(defToInline);
            } else {
              defToInline.delete();
            }
          }
        });

        if (inlineAll.get() && ReferencesSearch.search(local).findFirst() == null && editor != null) {
          QuickFixFactory.getInstance().createRemoveUnusedVariableFix(local).invoke(project, editor, local.getContainingFile());
        }

        if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
          highlightManager.addOccurrenceHighlights(editor, ContainerUtil.convert(exprs, new PsiExpression[refsToInline.length],
                                                                                 pointer -> pointer.getElement()), attributes, true, null);
          WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        }

        WriteAction.run(() -> {
          for (SmartPsiElementPointer<PsiExpression> expr : exprs) {
            InlineUtil.tryToInlineArrayCreationForVarargs(expr.getElement());
          }
        });
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(containingClass);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }
    };

    CommandProcessor.getInstance().executeCommand(project, () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(runnable), RefactoringBundle.message("inline.command", localName), null);
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
  public static PsiElement checkRefsInAugmentedAssignmentOrUnaryModified(final PsiElement[] refsToInline, PsiElement defToInline) {
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
                                      final PsiCodeBlock block,
                                      final boolean rethrow) {
    if (refExpr != null) {
      PsiElement def;
      if (refExpr instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression) refExpr)) {
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
}
