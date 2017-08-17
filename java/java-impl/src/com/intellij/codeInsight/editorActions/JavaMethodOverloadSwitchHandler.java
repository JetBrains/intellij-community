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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

abstract class JavaMethodOverloadSwitchHandler extends EditorActionHandler {
  private static final Key<Boolean> SWITCH_DISABLED = Key.create("switch.disabled");
  private static final Key<Map<String, String>> ENTERED_PARAMETERS = Key.create("entered.parameters");
  private final EditorActionHandler myOriginalHandler;
  private final boolean mySwitchUp;

  private JavaMethodOverloadSwitchHandler(EditorActionHandler originalHandler, boolean up) {
    myOriginalHandler = originalHandler;
    mySwitchUp = up;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (myOriginalHandler.isEnabled(editor, caret, dataContext)) return true;

    if (editor.getUserData(SWITCH_DISABLED) != null ||
        !CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION || !ParameterInfoController.existsForEditor(editor)) return false;

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
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null) caret = editor.getCaretModel().getPrimaryCaret();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null && editor.getUserData(SWITCH_DISABLED) == null &&
        CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION &&
        ParameterInfoController.existsWithVisibleHintForEditor(editor, false)) {
      doSwitch(editor, caret, project);
    }
    else {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }

  private void doSwitch(@NotNull final Editor editor, @NotNull Caret caret, @NotNull Project project) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiElement exprList = getExpressionList(editor, caret.getOffset(), project);
    if (!(exprList instanceof PsiExpressionList)) return;
    PsiElement call = exprList.getParent();
    if (!(call instanceof PsiCall)) return;
    int lbraceOffset = exprList.getTextRange().getStartOffset();
    ParameterInfoController controller = ParameterInfoController.findControllerAtOffset(editor, lbraceOffset);
    if (controller == null || !controller.isHintShown(false)) return;

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

    final PsiMethod targetMethod =
      (PsiMethod)((CandidateInfo)objects[(currentIndex + (mySwitchUp ? -1 : 1) + objects.length) % objects.length]).getElement();
    PsiParameterList parameterList = targetMethod.getParameterList();
    final int parametersCount = parameterList.getParametersCount();
    caret.moveToOffset(lbraceOffset); // avoid caret impact on hints location
    final int endOffset = exprList.getTextRange().getEndOffset() - 1;
    Map<String, String> finalEnteredParameters = enteredParameters;
    Ref<Integer> targetCaretPosition = new Ref<>();
    WriteAction.run(() -> {
      int offset = lbraceOffset + 1;
      editor.getDocument().deleteString(offset, endOffset);
      for (int i = 0; i < parametersCount; i++) {
        String key = getParameterKey(targetMethod, i);
        String value = finalEnteredParameters.getOrDefault(key, "");
        if (value.isEmpty() && targetCaretPosition.isNull()) targetCaretPosition.set(offset);
        if (i < parametersCount - 1) value += ", ";
        editor.getDocument().insertString(offset, value);
        offset += value.length();
      }
      if (targetCaretPosition.isNull()) targetCaretPosition.set(offset);
    });
    caret.moveToLogicalPosition(editor.offsetToLogicalPosition(targetCaretPosition.get()).leanForward(true));
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

  public static class Up extends JavaMethodOverloadSwitchHandler {
    public Up(EditorActionHandler originalHandler) {
      super(originalHandler, true);
    }
  }

  public static class Down extends JavaMethodOverloadSwitchHandler {
    public Down(EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }
  }

  public static class UpInEditor extends UpDownInEditor {
    public UpInEditor(EditorActionHandler originalHandler) {
      super(originalHandler, true);
    }
  }

  public static class DownInEditor extends UpDownInEditor {
    public DownInEditor(EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }
  }
  
  private static abstract class UpDownInEditor extends EditorActionHandler {
    private final EditorActionHandler myOriginalHandler;
    private final boolean myUp;
    
    private UpDownInEditor(EditorActionHandler originalHandler, boolean up) {
      myOriginalHandler = originalHandler;
      myUp = up;
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return myOriginalHandler.isEnabled(editor, caret, dataContext) ||
             isEnabled(editor);
    }

    private static boolean isEnabled(@NotNull Editor editor) {
      return CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION &&
             ParameterInfoController.existsWithVisibleHintForEditor(editor, false);
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (isEnabled(editor)) {
        ParameterInfoController.hideAllHints(editor);
      }
      // hints can be hidden asynchronously (with animation), so we disable switching explicitly here
      editor.putUserData(SWITCH_DISABLED, Boolean.TRUE);
      try {
        if (myOriginalHandler.isEnabled(editor, caret, dataContext)) {
          myOriginalHandler.execute(editor, caret, dataContext);
        }
        else {
          EditorActionManager.getInstance().getActionHandler(myUp ? IdeActions.ACTION_EDITOR_MOVE_CARET_UP 
                                                                  : IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
            .execute(editor, caret, dataContext);
        }
      }
      finally {
        editor.putUserData(SWITCH_DISABLED, null);
      }
    }
  }
}
