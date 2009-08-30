package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.codeInsight.TargetElementUtilBase;

/**
 * @author yole
 */
public class HighlightExitPointsHandlerFactory implements HighlightUsagesHandlerFactory {
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword) {
      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        return new HighlightExitPointsHandler(editor, file, target);
      }
    }
    return null;
  }
}
