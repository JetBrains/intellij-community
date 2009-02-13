package com.intellij.openapi.paths;

import com.intellij.psi.PsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;

/**
 * @author Dmitry Avdeev
 */
public class GenericDynamicContextProvider implements DynamicContextProvider {

  public int getOffset(PsiElement element, int offset, String elementText) {
    final PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (isDynamic(child)) {
        final int i = child.getStartOffsetInParent();
        if (i == offset) {  // dynamic context?
          final PsiElement next = child.getNextSibling();
          if (next == null || !next.getText().startsWith("/")) {
            return -1;
          }
          offset = next.getStartOffsetInParent();
        } else {
          final int pos = PathReferenceProviderBase.getLastPosOfURL(offset, elementText);
          if (pos == -1 || pos > i) {
            return -1;
          }
          return offset;
        }
      }
    }
    return offset;
  }

  protected boolean isDynamic(PsiElement child) {
    return child instanceof OuterLanguageElement;
  }

}
