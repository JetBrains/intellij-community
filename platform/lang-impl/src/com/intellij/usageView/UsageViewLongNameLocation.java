package com.intellij.usageView;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class UsageViewLongNameLocation extends ElementDescriptionLocation {
  private UsageViewLongNameLocation() {
  }

  public static final UsageViewLongNameLocation INSTANCE = new UsageViewLongNameLocation();

  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
      if (location instanceof UsageViewLongNameLocation) {
        if (element instanceof PsiDirectory) {
          return PsiDirectoryFactory.getInstance(element.getProject()).getQualifiedName((PsiDirectory)element, true);
        }
        return "";
      }
      return null;
    }
  };
}
