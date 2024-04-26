package com.intellij.dupLocator.equivalence;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class EquivalenceDescriptorProvider {
  public static final ExtensionPointName<EquivalenceDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.equivalenceDescriptorProvider");

  // for using in tests only !!!
  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static boolean ourUseDefaultEquivalence = false;

  public abstract boolean isMyContext(@NotNull PsiElement context);

  public abstract @Nullable EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element);

  // by default only PsiWhitespace ignored
  public TokenSet getIgnoredTokens() {
    return TokenSet.EMPTY;
  }

  public static @Nullable EquivalenceDescriptorProvider getInstance(@NotNull PsiElement context) {
    //noinspection TestOnlyProblems
    if (ourUseDefaultEquivalence) {
      return null;
    }

    for (EquivalenceDescriptorProvider descriptorProvider : EP_NAME.getExtensionList()) {
      if (descriptorProvider.isMyContext(context)) {
        return descriptorProvider;
      }
    }
    return null;
  }
}
