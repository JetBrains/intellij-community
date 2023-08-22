// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class JavaMethodOverloadSwitchHandler extends EditorActionHandler {
  private static final Key<Map<String, String>> ENTERED_PARAMETERS = Key.create("entered.parameters");
  private final boolean mySwitchUp;

  JavaMethodOverloadSwitchHandler(boolean up) {
    mySwitchUp = up;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION || 
        !ParameterInfoControllerBase.existsForEditor(editor)) return false;

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    PsiElement exprList = getExpressionList(editor, caret.getOffset(), project);
    if (exprList == null) return false;

    int lbraceOffset = exprList.getTextRange().getStartOffset();
    ParameterInfoControllerBase controller = ParameterInfoControllerBase.findControllerAtOffset(editor, lbraceOffset);
    return controller != null && controller.isHintShown(false);
  }

  @Nullable
  private static PsiElement getExpressionList(@NotNull Editor editor, int offset, @NotNull Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file != null ? ParameterInfoControllerBase.findArgumentList(file, offset, -1) : null;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null && CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION &&
        ParameterInfoControllerBase.existsWithVisibleHintForEditor(editor, false)) {
      doSwitch(editor, caret == null ? editor.getCaretModel().getPrimaryCaret() : caret, project);
    }
  }

  private void doSwitch(@NotNull final Editor editor, @NotNull Caret caret, @NotNull Project project) {
    if (editor.isViewer() || !EditorModificationUtil.requestWriting(editor)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiElement exprList = getExpressionList(editor, caret.getOffset(), project);
    if (!(exprList instanceof PsiExpressionList)) return;
    PsiElement call = exprList.getParent();
    if (!(call instanceof PsiCall methodCall)) return;
    int lbraceOffset = exprList.getTextRange().getStartOffset();
    ParameterInfoControllerBase controller = ParameterInfoControllerBase.findControllerAtOffset(editor, lbraceOffset);
    if (controller == null || !controller.isHintShown(false)) return;

    Object[] objects = controller.getObjects();
    Object highlighted = controller.getHighlighted();
    if (objects == null || objects.length <= 1) return;

    Map<String, String> enteredParameters = exprList.getUserData(ENTERED_PARAMETERS);
    if (enteredParameters == null) {
      exprList.putUserData(ENTERED_PARAMETERS, enteredParameters = new HashMap<>());
    }
    boolean virtualComma = Registry.is("editor.completion.hints.virtual.comma");
    int currentIndex;
    if (highlighted == null) {
      currentIndex = mySwitchUp ? objects.length : -1;
    }
    else {
      currentIndex = Arrays.asList(objects).indexOf(highlighted);
      if (currentIndex < 0) return;

      PsiMethod currentMethod = (PsiMethod)((CandidateInfo)objects[currentIndex]).getElement();
      int currentMethodParameterCount = currentMethod.getParameterList().getParametersCount();
      PsiExpression[] enteredExpressions = ((PsiExpressionList)exprList).getExpressions();
      int enteredCount = enteredExpressions.length;
      if (currentMethodParameterCount != enteredCount && !(enteredCount == 0 && currentMethodParameterCount == 1) &&
          (!virtualComma || !currentMethod.isVarArgs() && enteredCount > currentMethodParameterCount)) {
        // when parameter list has been edited, but popup wasn't updated for some reason
        return;
      }

      for (int i = 0; i < enteredExpressions.length; i++) {
        PsiExpression expression = enteredExpressions[i];
        String value = expression.getText().trim();
        if (!value.isEmpty()) {
          String key = getParameterKey(currentMethod, i);
          enteredParameters.put(key, value);
        }
      }
    }

    final PsiMethod targetMethod =
      (PsiMethod)((CandidateInfo)objects[(currentIndex + (mySwitchUp ? -1 : 1) + objects.length) % objects.length]).getElement();

    updateParameterValues(editor, caret, targetMethod, exprList, lbraceOffset, enteredParameters, virtualComma);

    JavaMethodCallElement.setCompletionModeIfNotSet(methodCall, controller);

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    CompletionMemory.registerChosenMethod(targetMethod, methodCall);
    controller.setPreservedOnHintHidden(true);
    ParameterHintsPass.asyncUpdate(call, editor);
    controller.showHint(false, false);
  }

  private static void updateParameterValues(@NotNull final Editor editor, @NotNull Caret caret, @NotNull PsiMethod targetMethod,
                                            @NotNull PsiElement exprList, int lbraceOffset, @NotNull Map<String, String> enteredParameters,
                                            boolean virtualComma) {
    PsiParameterList parameterList = targetMethod.getParameterList();
    final int parametersCount = parameterList.getParametersCount();
    caret.moveToOffset(lbraceOffset); // avoid caret impact on hints location
    final int endOffset = exprList.getTextRange().getEndOffset() - 1;
    int lastParameterWithValue = -1;
    if (virtualComma) {
      for (int i = parametersCount - 1; i >= 0; i--) {
        String key = getParameterKey(targetMethod, i);
        String value = enteredParameters.getOrDefault(key, "");
        if (!value.isEmpty()) {
          lastParameterWithValue = i;
          break;
        }
      }
    }
    int paramsToInsert = virtualComma ? lastParameterWithValue + 1 : parametersCount;
    Ref<Integer> targetCaretPosition = new Ref<>();
    WriteAction.run(() -> {
      int offset = lbraceOffset + 1;
      editor.getDocument().deleteString(offset, endOffset);
      for (int i = 0; i < paramsToInsert; i++) {
        String key = getParameterKey(targetMethod, i);
        String value = enteredParameters.getOrDefault(key, "");
        if (value.isEmpty() && targetCaretPosition.isNull()) targetCaretPosition.set(offset);
        if (i < parametersCount - 1 && (!virtualComma || i < paramsToInsert - 1 || targetCaretPosition.isNull())) value += ", ";
        editor.getDocument().insertString(offset, value);
        offset += value.length();
      }
      if (targetCaretPosition.isNull()) targetCaretPosition.set(offset);
    });
    caret.moveToOffset(targetCaretPosition.get());
  }

  private static String getParameterKey(PsiMethod method, int parameterIndex) {
    int parameterCount = method.getParameterList().getParametersCount();
    int mainIndex = method.isVarArgs() && parameterIndex >= parameterCount ? parameterCount - 1 : parameterIndex;
    int subIndex = parameterIndex - mainIndex;
    PsiParameter parameter = method.getParameterList().getParameters()[mainIndex];
    return parameter.getName() + ":" + parameter.getType().getCanonicalText() + (subIndex == 0 ? "" : (":" + subIndex));
  }
}
