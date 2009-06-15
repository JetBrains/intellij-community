package com.intellij.codeInsight.highlighting;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class HighlightUsagesDescriptionLocation extends ElementDescriptionLocation {
  private HighlightUsagesDescriptionLocation() {
  }

  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return new ElementDescriptionProvider() {
      public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
        if (element instanceof PsiPresentableMetaData) {
          return ((PsiPresentableMetaData)element).getTypeName();
        }
        return null;
      }
    };
  }

  public static HighlightUsagesDescriptionLocation INSTANCE = new HighlightUsagesDescriptionLocation();

}
