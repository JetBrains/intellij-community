/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;

public class MissingArrayInitializerBraceFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression || psiElement instanceof PsiArrayInitializerMemberValue)) return;
    if (!psiElement.getText().endsWith("}")) {
      PsiErrorElement err = ContainerUtil.findInstance(psiElement.getChildren(), PsiErrorElement.class);
      int endOffset = (err != null ? err : psiElement).getTextRange().getEndOffset();
      editor.getDocument().insertString(endOffset, "}");
    }
  }
}