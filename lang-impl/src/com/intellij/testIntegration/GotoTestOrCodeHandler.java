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

  public static PsiElement getSelectedElement(Editor editor, PsiFile file) {
    return PsiUtilBase.getElementAtOffset(file, editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean shouldSortResult() {
    return false;
  }

  protected String getChooserInFileTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.test.subject.in.file.chooser.title";
    } else {
      return "goto.test.in.file.chooser.title";
    }
  }

  protected String getChooserTitleKey(PsiElement sourceElement) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return "goto.test.subject.chooser.title";
    } else {
      return "goto.test.chooser.title";
    }
  }
}
