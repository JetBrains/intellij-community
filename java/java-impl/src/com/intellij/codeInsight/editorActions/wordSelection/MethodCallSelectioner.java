/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MethodCallSelectioner implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiMethodCallExpression;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)e;
    PsiElement referenceNameElement = methodCall.getMethodExpression().getReferenceNameElement();
    if (referenceNameElement == null) {
      return null;
    }
    else {
      return List.of(new TextRange(referenceNameElement.getTextRange().getStartOffset(), methodCall.getTextRange().getEndOffset()),
                     methodCall.getTextRange());
    }
  }
}
