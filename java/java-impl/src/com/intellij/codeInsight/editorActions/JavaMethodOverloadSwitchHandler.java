/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JavaMethodOverloadSwitchHandler extends EditorWriteActionHandler {
  private static final Key<Map<String, String>> ENTERED_PARAMETERS = Key.create("entered.parameters");
  private final boolean mySwitchUp;

  public JavaMethodOverloadSwitchHandler(boolean up) {
    mySwitchUp = up;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (!ParameterInfoController.areParametersHintsEnabledOnCompletion() || !ParameterInfoController.existsForEditor(editor)) return false;

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    PsiElement exprList = getExpressionList(editor, caret.getOffset(), project);
    if (exprList == null) return false;

    int lbraceOffset = exprList.getTextRange().getStartOffset();
    return ParameterInfoController.findControllerAtOffset(editor, lbraceOffset) != null;
  }

  @Nullable
  private static PsiElement getExpressionList(@NotNull Editor editor, int offset, @NotNull Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file != null ? ParameterInfoController.findArgumentList(file, offset, -1) : null;
  }

  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null) caret = editor.getCaretModel().getPrimaryCaret();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement exprList = getExpressionList(editor, caret.getOffset(), project);
    if (!(exprList instanceof PsiExpressionList)) return;
    PsiElement call = exprList.getParent();
    if (!(call instanceof PsiCall)) return;

    int lbraceOffset = exprList.getTextRange().getStartOffset();
    ParameterInfoController controller = ParameterInfoController.findControllerAtOffset(editor, lbraceOffset);
    if (controller == null) return;
    Object[] objects = controller.getObjects();
    Object highlighted = controller.getHighlighted();
    if (objects == null || objects.length <= 1 || highlighted == null) return;

    int currentIndex = ContainerUtil.indexOf(Arrays.asList(objects), highlighted);
    if (currentIndex < 0) return;

    PsiMethod currentMethod = (PsiMethod)((CandidateInfo)objects[currentIndex]).getElement();
    int currentMethodParameterCount = currentMethod.getParameterList().getParametersCount();
    PsiExpression[] enteredExpressions = ((PsiExpressionList)exprList).getExpressions();
    int enteredCount = enteredExpressions.length;
    if (currentMethodParameterCount != enteredCount && !(enteredCount == 0 && currentMethodParameterCount == 1)) {
      // when parameter list has been edited, but popup wasn't updated for some reason
      return;
    }

    Map<String, String> enteredParameters = exprList.getUserData(ENTERED_PARAMETERS);
    if (enteredParameters == null) {
      exprList.putUserData(ENTERED_PARAMETERS, enteredParameters = new HashMap<>());
    }
    for (int i = 0; i < enteredExpressions.length; i++) {
      PsiExpression expression = enteredExpressions[i];
      String value = expression.getText().trim();
      if (!value.isEmpty()) {
        String key = getParameterKey(currentMethod, i);
        enteredParameters.put(key, value);
      }
    }

    PsiMethod targetMethod = (PsiMethod)((CandidateInfo)objects[(currentIndex + (mySwitchUp ? -1 : 1) + objects.length) % objects.length]).getElement();
    PsiParameterList parameterList = targetMethod.getParameterList();
    int parametersCount = parameterList.getParametersCount();
    caret.moveToOffset(lbraceOffset); // avoid caret impact on hints location
    int offset = lbraceOffset + 1;
    int endOffset = exprList.getTextRange().getEndOffset() - 1;
    editor.getDocument().deleteString(offset, endOffset);
    int targetCaretPosition = -1;
    for (int i = 0; i < parametersCount; i++) {
      String key = getParameterKey(targetMethod, i);
      String value = enteredParameters.getOrDefault(key, "");
      if (value.isEmpty() && targetCaretPosition == -1) targetCaretPosition = offset;
      if (i < parametersCount - 1) value += ", ";
      editor.getDocument().insertString(offset, value);
      offset += value.length();
    }
    if (targetCaretPosition == -1) targetCaretPosition = offset;
    caret.moveToLogicalPosition(editor.offsetToLogicalPosition(targetCaretPosition).leanForward(true));
    PsiCall methodCall = (PsiCall)call;
    if (!JavaMethodCallElement.isCompletionMode(methodCall)) {
      JavaMethodCallElement.setCompletionMode(methodCall, true);
      Disposer.register(controller, () -> JavaMethodCallElement.setCompletionMode(methodCall, false));
    }

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    CompletionMemory.registerChosenMethod(targetMethod, methodCall);
    controller.resetHighlighted();
    controller.updateComponent(); // update popup immediately (otherwise, it will be updated only after delay)
    ParameterHintsPass.syncUpdate(call, editor);
  }

  private static String getParameterKey(PsiMethod method, int parameterIndex) {
    PsiParameter parameter = method.getParameterList().getParameters()[parameterIndex];
    return parameter.getName() + ":" + parameter.getType().getCanonicalText();
  }
}
