package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Excludes scope from use scope of PSI element. The extension should be used only for optimization, i.e. it should throw off scopes
 * which can't contain references to provided PSI element.
 *
 * @author Konstantin.Ulitin
 */
public abstract class UseScopeOptimizer {
  public static final ExtensionPointName<UseScopeOptimizer> EP_NAME = ExtensionPointName.create("com.intellij.useScopeOptimizer");

  @Nullable
  public abstract GlobalSearchScope getScopeToExclude(@NotNull PsiElement element);
}
