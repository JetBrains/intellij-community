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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class GotoImplementationHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.implementation";
  }

  public Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    PsiElement[] target = new ImplementationSearcher().searchImplementations(editor, source, offset);
    if (target.length == 0) {
      return new Pair<PsiElement, PsiElement[]>(source, new PsiElement[] { source });
    }
    return new Pair<PsiElement, PsiElement[]>(source, target);
  }

  protected String getChooserInFileTitleKey(PsiElement sourceElement) {
    return "goto.implementation.in.file.chooser.title";
  }

  protected String getChooserTitleKey(PsiElement sourceElement) {
    return "goto.implementation.chooser.title";
  }

}
