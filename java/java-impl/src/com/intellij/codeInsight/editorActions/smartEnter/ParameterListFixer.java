/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameterList;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 * @since Sep 5, 2003
 */
public class ParameterListFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiParameterList || psiElement instanceof PsiAnnotationParameterList) {
      String text = psiElement.getText();
      if (StringUtil.startsWithChar(text, '(') && !StringUtil.endsWithChar(text, ')')) {
        PsiElement[] params = psiElement instanceof PsiParameterList ? ((PsiParameterList)psiElement).getParameters() 
                                                                     : ((PsiAnnotationParameterList)psiElement).getAttributes();
        int offset;
        if (params.length == 0) {
          offset = psiElement.getTextRange().getStartOffset() + 1;
        }
        else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
