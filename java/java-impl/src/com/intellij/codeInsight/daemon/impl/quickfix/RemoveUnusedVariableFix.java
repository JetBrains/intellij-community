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
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedVariableFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix");
  private final PsiVariable myVariable;

  public RemoveUnusedVariableFix(PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message(myVariable instanceof PsiField ? "remove.unused.field" : "remove.unused.variable",
                                  myVariable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.variable.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      myVariable != null
      && myVariable.isValid()
      && myVariable.getManager().isInProject(myVariable)
      ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;
    removeVariableAndReferencingStatements(editor);
  }

  private void removeVariableAndReferencingStatements(Editor editor) {
    final List<PsiElement> references = new ArrayList<>();
    final List<PsiElement> sideEffects = new ArrayList<>();
    final boolean[] canCopeWithSideEffects = {true};
    try {
      PsiElement context = myVariable instanceof PsiField ? ((PsiField)myVariable).getContainingClass() : PsiUtil.getVariableCodeBlock(myVariable, null);
      if (context != null) {
        RemoveUnusedVariableUtil.collectReferences(context, myVariable, references);
      }
      // do not forget to delete variable declaration
      references.add(myVariable);
      // check for side effects
      for (PsiElement element : references) {
        Boolean result = RemoveUnusedVariableUtil.processUsage(element, myVariable, sideEffects, RemoveUnusedVariableUtil.RemoveMode.CANCEL);
        if (result == null) return;
        canCopeWithSideEffects[0] &= result;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    final RemoveUnusedVariableUtil.RemoveMode
      deleteMode = showSideEffectsWarning(sideEffects, myVariable, editor, canCopeWithSideEffects[0]);

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        RemoveUnusedVariableUtil.deleteReferences(myVariable, references, deleteMode);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  public static RemoveUnusedVariableUtil.RemoveMode showSideEffectsWarning(List<PsiElement> sideEffects,
                                           PsiVariable variable,
                                           Editor editor,
                                           boolean canCopeWithSideEffects,
                                           @NonNls String beforeText,
                                           @NonNls String afterText) {
    if (sideEffects.isEmpty()) return RemoveUnusedVariableUtil.RemoveMode.DELETE_ALL;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return canCopeWithSideEffects
             ? RemoveUnusedVariableUtil.RemoveMode.MAKE_STATEMENT
             : RemoveUnusedVariableUtil.RemoveMode.DELETE_ALL;
    }
    Project project = editor.getProject();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    PsiElement[] elements = PsiUtilCore.toPsiElementArray(sideEffects);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, true, null);

    SideEffectWarningDialog dialog = new SideEffectWarningDialog(project, false, variable, beforeText, afterText, canCopeWithSideEffects);
    dialog.show();
    int code = dialog.getExitCode();
    return RemoveUnusedVariableUtil.RemoveMode.values()[code];
  }

  private static RemoveUnusedVariableUtil.RemoveMode showSideEffectsWarning(List<PsiElement> sideEffects,
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
