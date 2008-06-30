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

  protected Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    PsiElement[] target = new ImplementationSearcher().searchImplementations(editor, source, offset);
    return new Pair<PsiElement, PsiElement[]>(source, target);
  }

  protected String getChooserInFileTitleKey(PsiElement sourceElement) {
    return "goto.implementation.in.file.chooser.title";
  }

  protected String getChooserTitleKey(PsiElement sourceElement) {
    return "goto.implementation.chooser.title";
  }

}
