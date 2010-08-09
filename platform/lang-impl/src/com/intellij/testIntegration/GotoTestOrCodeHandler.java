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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class GotoTestOrCodeHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.testOrCode";
  }

  @Nullable
  protected GotoData getSourceAndTargetElements(final Editor editor, final PsiFile file) {
    PsiElement selectedElement = getSelectedElement(editor, file);
    PsiElement sourceElement = TestFinderHelper.findSourceElement(selectedElement);
    if (sourceElement == null) return null;

    List<AdditionalAction> actions = new SmartList<AdditionalAction>();

    Collection<PsiElement> candidates;
    if (TestFinderHelper.isTest(selectedElement)) {
      candidates = TestFinderHelper.findClassesForTest(selectedElement);
    }
    else {
      candidates = TestFinderHelper.findTestsForClass(selectedElement);
      final TestCreator creator = LanguageTestCreators.INSTANCE.forLanguage(file.getLanguage());
      if (creator != null && creator.isAvailable(file.getProject(), editor, file)) {
        actions.add(new AdditionalAction() {
          @Override
          public String getText() {
            return "Create New Test...";
          }

          @Override
          public Icon getIcon() {
            return IconLoader.getIcon("/actions/intentionBulb.png");
          }

          @Override
          public void execute() {
            creator.createTest(file.getProject(), editor, file);
          }
        });
      }
    }

    return new GotoData(sourceElement, candidates.toArray(new PsiElement[candidates.size()]), actions);
  }

  @NotNull
  public static PsiElement getSelectedElement(Editor editor, PsiFile file) {
    return PsiUtilBase.getElementAtOffset(file, editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean shouldSortTargets() {
    return false;
  }

  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return CodeInsightBundle.message("goto.test.chooserTitle.subject", name, length);
    }
    else {
      return CodeInsightBundle.message("goto.test.chooserTitle.test", name, length);
    }
  }

  @Override
  protected String getNotFoundMessage(Project project, Editor editor, PsiFile file) {
    return CodeInsightBundle.message("goto.test.notFound");
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
}
