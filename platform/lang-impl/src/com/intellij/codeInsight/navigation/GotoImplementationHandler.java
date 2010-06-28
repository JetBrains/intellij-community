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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.Collections;

public class GotoImplementationHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.implementation";
  }

  public GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    if (source == null) return null;
    return new GotoData(source, new ImplementationSearcher().searchImplementations(editor, source, offset), Collections.EMPTY_LIST);
  }

  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.chooserTitle", name, length);
  }

  @Override
  protected String getNotFoundMessage(Project project, Editor editor, PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

}
