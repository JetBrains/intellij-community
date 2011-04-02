
package com.intellij.openapi.paths;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class PathReferenceProviderBase implements PathReferenceProvider {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.paths.PathReferenceProviderBase");

  public boolean createReferences(@NotNull final PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft) {

    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(psiElement);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(psiElement);
    int offset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    boolean dynamicContext = false;
    final String elementText = psiElement.getText();
    for (DynamicContextProvider provider: Extensions.getExtensions(DynamicContextProvider.EP_NAME)) {
      final int dynamicOffset = provider.getOffset(psiElement, offset, elementText);
      if (dynamicOffset == -1) {
        return false;
      } else if (dynamicOffset != offset) {
        dynamicContext = true;
        offset = dynamicOffset;
      }
    }

    final int pos = getLastPosOfURL(offset, elementText);
    if (pos != -1 && pos < endOffset) {
      endOffset = pos;
    }
    try {
      final String text = elementText.substring(offset, endOffset);
      return createReferences(psiElement, offset, text, references, soft || dynamicContext);
    } catch (StringIndexOutOfBoundsException e) {
      LOG.error("Cannot process string: '" + psiElement.getParent().getParent().getText() + "'", e);
      return false;
    }
  }

  public abstract boolean createReferences(@NotNull final PsiElement psiElement,
                                  final int offset,
                                  String text,
                                  final @NotNull List<PsiReference> references,
                                  final boolean soft);

  public static int getLastPosOfURL(final int offset, @NotNull String url) {
    for (int i = offset; i < url.length(); i++) {
      switch (url.charAt(i)) {
        case '?':
        case '#':
          return i;
      }
    }
    return -1;
  }

}
