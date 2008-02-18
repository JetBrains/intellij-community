package com.intellij.ide.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DeleteNameDescriptionLocation implements ElementDescriptionLocation {
  private DeleteNameDescriptionLocation() {
  }

  public static DeleteNameDescriptionLocation INSTANCE = new DeleteNameDescriptionLocation();
  private static ElementDescriptionProvider ourDefaultProvider = new DefaultProvider();

  public ElementDescriptionProvider getDefaultProvider() {
    return ourDefaultProvider;
  }

  public static class DefaultProvider implements ElementDescriptionProvider {
    public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
      if (location instanceof DeleteNameDescriptionLocation) {
        if (element instanceof PsiNamedElement) {
          return ((PsiNamedElement)element).getName();
        }
      }
      return null;
    }
  }
}
