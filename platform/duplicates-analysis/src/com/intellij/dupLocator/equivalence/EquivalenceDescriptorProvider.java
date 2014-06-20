package com.intellij.dupLocator.equivalence;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class EquivalenceDescriptorProvider {
  public static final ExtensionPointName<EquivalenceDescriptorProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.equivalenceDescriptorProvider");

  // for using in tests only !!!
  public static boolean ourUseDefaultEquivalence = false;

  public abstract boolean isMyContext(@NotNull PsiElement context);

  @Nullable
  public abstract EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element);

  // by default only PsiWhitespace ignored
  public TokenSet getIgnoredTokens() {
    return TokenSet.EMPTY;
  }

  @Nullable
  public static EquivalenceDescriptorProvider getInstance(@NotNull PsiElement context) {
    if (ourUseDefaultEquivalence) {
      return null;
    }

    for (EquivalenceDescriptorProvider descriptorProvider : EP_NAME.getExtensions()) {
      if (descriptorProvider.isMyContext(context)) {
        return descriptorProvider;
      }
    }
    return null;
  }
}
