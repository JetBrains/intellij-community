package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author yole
 */
public class HighlightSuppressedWarningsFactory implements HighlightUsagesHandlerFactory {
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement target = file.findElementAt(offset);
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(target, PsiAnnotation.class);
    if (annotation != null && Comparing.strEqual(SuppressWarnings.class.getName(), annotation.getQualifiedName())) {
      return new HighlightSuppressedWarningsHandler(editor, file, annotation, PsiTreeUtil.getParentOfType(target, PsiLiteralExpression.class));
    }
    return null;
  }
}