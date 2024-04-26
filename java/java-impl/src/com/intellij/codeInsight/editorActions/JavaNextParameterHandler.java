// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class JavaNextParameterHandler extends EditorActionHandler {
  private final EditorActionHandler myDelegate;

  public JavaNextParameterHandler(EditorActionHandler delegate) {
    myDelegate = delegate;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return myDelegate.isEnabled(editor, caret, dataContext);
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    int offset = caret != null ? caret.getOffset() : editor.getCaretModel().getOffset();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file instanceof PsiJavaFile) {
        PsiElement exprList = ParameterInfoControllerBase.findArgumentList(file, offset, -1);
        if (exprList instanceof PsiExpressionList list) {
          CharSequence text = editor.getDocument().getImmutableCharSequence();
          int next = CharArrayUtil.shiftForward(text, offset, " \t");
          int actualParameterCount = list.getExpressionCount();
          int lastParamStart = actualParameterCount == 0 ? list.getTextOffset() + 1
                                                         : list.getExpressions()[actualParameterCount - 1].getTextRange().getStartOffset();
          if (next >= lastParamStart) {
            int prev = CharArrayUtil.shiftBackward(text, lastParamStart - 1, " \t");
            char prevChar = prev >= 0 && prev < editor.getDocument().getTextLength() ? text
              .charAt(prev) : 0;
            if (prevChar == ',' || prevChar == '(') {
              int listOffset = exprList.getTextRange().getStartOffset();
              ParameterInfoControllerBase controller = ParameterInfoControllerBase.findControllerAtOffset(editor, listOffset);
              if (controller != null) {
                Object[] objects = controller.getObjects();
                Object highlighted = controller.getHighlighted();
                if (objects != null && objects.length > 0 && (highlighted != null || objects.length == 1)) {
                  int currentIndex = highlighted == null ? 0 : Arrays.asList(objects).indexOf(highlighted);
                  int rParOffset = list.getTextRange().getEndOffset() - 1;

                  boolean checkTabOut = CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
                  int tabOutOffset = checkTabOut ? TabOutScopesTracker.getInstance().getScopeEndingAt(editor, offset) : -1;
                  if (currentIndex >= 0 && (tabOutOffset <= offset || rParOffset < tabOutOffset)) {
                    PsiMethod currentMethod = MethodParameterInfoHandler.getMethodFromCandidate(objects[currentIndex]);
                    if (currentMethod.isVarArgs() || actualParameterCount < currentMethod.getParameterList().getParametersCount() &&
                                                     currentMethod.getParameterList().getParametersCount() > 1) {
                      boolean lastParameterIsEmpty = CharArrayUtil.containsOnlyWhiteSpaces(text.subSequence(prev + 1, rParOffset));
                      if (lastParameterIsEmpty && currentMethod.isVarArgs()) {
                        if (prevChar == ',') {
                          WriteAction.run(() -> editor.getDocument().deleteString(prev, rParOffset));
                        }
                      }
                      else {
                        WriteAction.run(() -> editor.getDocument().insertString(rParOffset, ", "));
                      }
                      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                      PsiElement call = list.getParent();
                      if (call != null) ParameterHintsPass.asyncUpdate(call, editor);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    myDelegate.execute(editor, caret, dataContext);
  }
}
