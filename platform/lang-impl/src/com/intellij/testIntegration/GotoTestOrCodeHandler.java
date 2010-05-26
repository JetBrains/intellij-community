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

package com.intellij.testIntegration;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class GotoTestOrCodeHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.testOrCode";
  }

  protected Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file) {
    PsiElement selectedElement = getSelectedElement(editor, file);

    Collection<PsiElement> candidates;
    if (TestFinderHelper.isTest(selectedElement)) {
      candidates = TestFinderHelper.findClassesForTest(selectedElement);
    }
    else {
      candidates = TestFinderHelper.findTestsForClass(selectedElement);
    }

    PsiElement sourceElement = TestFinderHelper.findSourceElement(selectedElement);
    return new Pair<PsiElement, PsiElement[]>(sourceElement, candidates.toArray(new PsiElement[candidates.size()]));
  }

  @NotNull
  public static PsiElement getSelectedElement(Editor editor, PsiFile file) {
    return PsiUtilBase.getElementAtOffset(file, editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean shouldSortResult() {
    return false;
  }

  @Override
  protected void handleNoVariansCase(Project project, Editor editor, PsiFile file) {
    PsiElement selectedElement = getSelectedElement(editor, file);
    if (TestFinderHelper.isTest(selectedElement)) {
      HintManager.getInstance().showErrorHint(editor, ActionsBundle.message("action.GotoTestSubject.nothing.found"));
    }
  }

  protected String getChooserInFileTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.test.subject.in.file.chooser.title";
    }
    else {
      return "goto.test.in.file.chooser.title";
    }
  }

  protected String getChooserTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.test.subject.chooser.title";
    }
    else {
      return "goto.test.chooser.title";
    }
  }

  @Override
  protected void navigateToElement(Navigatable element) {
    if (element instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement)element);
    }
    else {
      element.navigate(true);
    }
  }

  @Override
  protected void navigateToElement(@Nullable Object element, @NotNull Editor editor, @NotNull PsiFile file) {
    final TestCreator creator = LanguageTestCreators.INSTANCE.forLanguage(file.getLanguage());
    if (creator != null) creator.createTest(file.getProject(), editor, file);
  }

  @Override
  protected boolean hasNullUsage() {
    return true;
  }
}
