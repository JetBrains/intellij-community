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

import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.JavaResolveResult;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 3:25:56 PM
 * To change this template use Options | File Templates.
 */
public class CompletionFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) psiElement;
      final JavaResolveResult resolved = ref.advancedResolve(true);
      if (resolved == null || resolved.getElement() == null) {
        editor.getCaretModel().moveToOffset(ref.getTextRange().getEndOffset());
        final CodeCompletionAction completionAction = (CodeCompletionAction) ActionManager.getInstance().getAction("CodeCompletion");
        completionAction.getHandler().invoke(psiElement.getProject(), editor, psiElement.getContainingFile());
      }
    }
  }
}
