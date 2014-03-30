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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;

/**
 * @author yole
 */
public class HighlightOverridingMethodsHandlerFactory implements HighlightUsagesHandlerFactory {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword && (PsiKeyword.EXTENDS.equals(target.getText()) || PsiKeyword.IMPLEMENTS.equals(target.getText()))) {
      PsiElement parent = target.getParent();
      if (!(parent instanceof PsiReferenceList)) return null;
      PsiElement grand = parent.getParent();
      if (!(grand instanceof PsiClass)) return null;
      return new HighlightOverridingMethodsHandler(editor, file, target, (PsiClass) grand);
    }
    return null;
  }
}
