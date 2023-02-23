/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil.RemoveMode;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedVariableFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(RemoveUnusedVariableFix.class);
  private final PsiVariable myVariable;

  public RemoveUnusedVariableFix(PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @NotNull
  public String getText() {
    return CommonQuickFixBundle.message("fix.remove.title.x", JavaElementKind.fromElement(myVariable).object(), myVariable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.element.family", JavaElementKind.VARIABLE.object());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      myVariable != null
      && myVariable.isValid()
      && BaseIntentionAction.canModify(myVariable)
      ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;
    removeVariableAndReferencingStatements(editor);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiVariable variable = PsiTreeUtil.findSameElementInCopy(myVariable, file);
    List<PsiElement> references = collectReferences(variable);
    // check for side effects
    final List<PsiElement> sideEffects = new ArrayList<>();
    boolean canCopeWithSideEffects = true;
    for (PsiElement element : references) {
      Boolean result = RemoveUnusedVariableUtil.processUsage(element, variable, sideEffects, RemoveMode.CANCEL);
      if (result == null) return IntentionPreviewInfo.EMPTY;
      canCopeWithSideEffects &= result;
    }
    RemoveMode mode = canCopeWithSideEffects && !sideEffects.isEmpty() ? RemoveMode.MAKE_STATEMENT : RemoveMode.DELETE_ALL;
    RemoveUnusedVariableUtil.deleteReferences(variable, references, mode);
    return IntentionPreviewInfo.DIFF;
  }

  private void removeVariableAndReferencingStatements(Editor editor) {
    record Context(RemoveMode deleteMode, List<PsiElement> references) {}

    ReadAction.nonBlocking(() -> {
      final List<PsiElement> references = collectReferences(myVariable);
      final List<PsiElement> sideEffects = new ArrayList<>();
      boolean canCopeWithSideEffects = true;
      // check for side effects
      for (PsiElement element : references) {
        Boolean result = RemoveUnusedVariableUtil.processUsage(element, myVariable, sideEffects, RemoveMode.CANCEL);
        if (result == null) return null;
        canCopeWithSideEffects &= result;
      }

      final RemoveMode deleteMode = showSideEffectsWarning(sideEffects, myVariable, editor, canCopeWithSideEffects);
      return new Context(deleteMode, references);
    }).finishOnUiThread(ModalityState.NON_MODAL, context -> {
        WriteCommandAction.writeCommandAction(myVariable.getProject()).run(() -> {
          try {
            RemoveUnusedVariableUtil.deleteReferences(myVariable, context.references, context.deleteMode);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        });
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static List<PsiElement> collectReferences(@NotNull PsiVariable variable) {
    List<PsiElement> references = new ArrayList<>();
    PsiElement context = variable instanceof PsiField ? ((PsiField)variable).getContainingClass() : PsiUtil.getVariableCodeBlock(variable, null);
    if (context != null) {
      RemoveUnusedVariableUtil.collectReferences(context, variable, references);
    }
    // do not forget to delete variable declaration
    references.add(variable);
    return references;
  }

  public static RemoveMode showSideEffectsWarning(List<? extends PsiElement> sideEffects,
                                                                           PsiVariable variable,
                                                                           Editor editor,
                                                                           boolean canCopeWithSideEffects,
                                                                           @NonNls String beforeText,
                                                                           @NonNls String afterText) {
    if (sideEffects.isEmpty()) return RemoveMode.DELETE_ALL;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return canCopeWithSideEffects ? RemoveMode.MAKE_STATEMENT : RemoveMode.DELETE_ALL;
    }
    Project project = editor.getProject();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    PsiElement[] elements = PsiUtilCore.toPsiElementArray(sideEffects);
    highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);

    SideEffectWarningDialog dialog = new SideEffectWarningDialog(project, false, variable, beforeText, afterText, canCopeWithSideEffects);
    dialog.show();
    int code = dialog.getExitCode();
    return RemoveMode.values()[code];
  }

  private static RemoveMode showSideEffectsWarning(List<? extends PsiElement> sideEffects,
                                                                            PsiVariable variable,
                                                                            Editor editor,
                                                                            boolean canCopeWithSideEffects) {
    String text;
    if (sideEffects.isEmpty()) {
      text = "";
    }
    else {
      final PsiElement sideEffect = sideEffects.get(0);
      if (sideEffect instanceof PsiExpression) {
        text = PsiExpressionTrimRenderer.render((PsiExpression)sideEffect);
      }
      else {
        text = sideEffect.getText();
      }
    }
    return showSideEffectsWarning(sideEffects, variable, editor, canCopeWithSideEffects, text, text);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
