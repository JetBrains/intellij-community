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

import com.intellij.codeInsight.hint.ParameterInfoController;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaVarArgNextParameterHandler extends EditorActionHandler {
  private final EditorActionHandler myDelegate;

  public JavaVarArgNextParameterHandler(EditorActionHandler delegate) {
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
        PsiElement exprList = ParameterInfoController.findArgumentList(file, offset, -1);
        if (exprList instanceof PsiExpressionList) {
          CharSequence text = editor.getDocument().getImmutableCharSequence();
          int next = CharArrayUtil.shiftForward(text, offset, " \t");
          PsiExpressionList list = (PsiExpressionList)exprList;
          int actualParameterCount = list.getExpressions().length;
          int lastParamStart = actualParameterCount == 0 ? list.getTextOffset() + 1 : list.getExpressions()[actualParameterCount - 1].getTextOffset();
          if (next >= lastParamStart) {
            int prev = CharArrayUtil.shiftBackward(text, lastParamStart - 1, " \t");
            char prevChar = prev >= 0 && prev < editor.getDocument().getTextLength() ? text
              .charAt(prev) : 0;
            if (prevChar == ',' || prevChar == '(') {
              int listOffset = exprList.getTextRange().getStartOffset();
              ParameterInfoController controller = ParameterInfoController.findControllerAtOffset(editor, listOffset);
              if (controller != null) {
                Object[] objects = controller.getObjects();
                Object highlighted = controller.getHighlighted();
                if (objects != null && objects.length > 0 && (highlighted != null || objects.length == 1)) {
                  int currentIndex = highlighted == null ? 0 : ContainerUtil.indexOf(Arrays.asList(objects), highlighted);
                  if (currentIndex >= 0) {
                    PsiMethod currentMethod = (PsiMethod)((CandidateInfo)objects[currentIndex]).getElement();
                    if (currentMethod.isVarArgs()) {
                      int rParOffset = list.getTextRange().getEndOffset() - 1;
                      boolean lastParameterIsEmpty = CharArrayUtil.containsOnlyWhiteSpaces(text.subSequence(prev + 1, rParOffset));
                      if (lastParameterIsEmpty) {
                        if (prevChar == ',') {
                          WriteAction.run(() -> editor.getDocument().deleteString(prev, rParOffset));
                        }
                      }
                      else {
                        WriteAction.run(() -> editor.getDocument().insertString(rParOffset, ", "));
                      }
                      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                      PsiElement call = list.getParent();
                      if (call != null) ParameterHintsPass.syncUpdate(call, editor);
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
