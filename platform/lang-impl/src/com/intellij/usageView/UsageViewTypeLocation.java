package com.intellij.usageView;

import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.TypeNameManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class UsageViewTypeLocation extends ElementDescriptionLocation {
  private UsageViewTypeLocation() {
  }

  public static final UsageViewTypeLocation INSTANCE = new UsageViewTypeLocation();

  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    public String getElementDescription(@NotNull final PsiElement psiElement, @NotNull final ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewTypeLocation)) return null;

      if (psiElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)psiElement).getMetaData();
        if (metaData instanceof PsiPresentableMetaData) {
          return ((PsiPresentableMetaData)metaData).getTypeName();
        }
      }

      if (psiElement instanceof PsiFile) {
        return LangBundle.message("terms.file");
      }
      if (psiElement instanceof PsiDirectory) {
        return LangBundle.message("terms.directory");
      }

      final Language lang = psiElement.getLanguage();
      FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      final String type = provider.getType(psiElement);
      if (StringUtil.isNotEmpty(type)) {
        return type;
      }

      return TypeNameManager.getTypeName(psiElement.getClass());
    }
  };
}