package com.intellij.usageView;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UsageViewShortNameLocation implements ElementDescriptionLocation {
  private UsageViewShortNameLocation() {
  }

  public static final UsageViewShortNameLocation INSTANCE = new UsageViewShortNameLocation();

  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewShortNameLocation)) return null;

      if (element instanceof PsiMetaOwner) {
        PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
        if (metaData!=null) return UsageViewUtil.getMetaDataName(metaData);
      }

      if (element instanceof PsiNamedElement) {
        return ((PsiNamedElement)element).getName();
      }
      return "";
    }
  };
}
