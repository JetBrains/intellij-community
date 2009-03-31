package com.intellij.usageView;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class UsageViewNodeTextLocation extends ElementDescriptionLocation {
  private UsageViewNodeTextLocation() {
  }

  public static final UsageViewNodeTextLocation INSTANCE = new UsageViewNodeTextLocation();

  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewNodeTextLocation)) return null;

      if (element instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
        if (metaData instanceof PsiPresentableMetaData) {
          return ((PsiPresentableMetaData)metaData).getTypeName() + " " + UsageViewUtil.getMetaDataName(metaData);
        }
      }

      if (element instanceof PsiFile) {
        return ((PsiFile) element).getName();
      }
      FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage());
      return provider.getNodeText(element, true);
    }
  };
}