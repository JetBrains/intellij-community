package com.intellij.testIntegration;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

import java.util.Collection;

public class GotoTestOrCodeHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.testOrCode";
  }

  protected Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file) {
    PsiElement selectedElement = PsiUtilBase.getElementAtOffset(file,
                                                                editor.getCaretModel().getOffset());

    Collection<PsiElement> candidates;
    if (TestFinderHelper.isTest(selectedElement)) {
      candidates = TestFinderHelper.findClassesForTest(selectedElement);
    }
    else {
      candidates = TestFinderHelper.findTestsForClass(selectedElement);
    }

    return new Pair<PsiElement, PsiElement[]>(selectedElement, candidates.toArray(new PsiElement[candidates.size()]));
  }

  protected String getChooserInFileTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.code.in.file.chooser.title";
    } else {
      return "goto.test.in.file.chooser.title";
    }
  }

  protected String getChooserTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.code.chooser.title";
    } else {
      return "goto.test.chooser.title";
    }
  }
}
