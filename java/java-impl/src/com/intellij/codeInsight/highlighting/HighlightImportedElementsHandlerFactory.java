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
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class HighlightImportedElementsHandlerFactory implements HighlightUsagesHandlerFactory {

  @Nullable
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(Editor editor, PsiFile file) {
    final int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement target = file.findElementAt(offset);
    if (!(target instanceof PsiKeyword) || !PsiKeyword.IMPORT.equals(target.getText())) {
      return null;
    }
    final PsiElement parent = target.getParent();
    if (!(parent instanceof PsiImportStatementBase)) {
      return null;
    }
    final PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiImportList)) {
      return null;
    }
    return new HighlightImportedElementsHandler(editor, file, target, (PsiImportStatementBase) parent);
  }
}
