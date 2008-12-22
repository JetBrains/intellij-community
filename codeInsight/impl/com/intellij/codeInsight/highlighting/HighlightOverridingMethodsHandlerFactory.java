package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.codeInsight.TargetElementUtilBase;

/**
 * @author yole
 */
public class HighlightOverridingMethodsHandlerFactory implements HighlightUsagesHandlerFactory {
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
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
