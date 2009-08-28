package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class FindUsagesHandlerFactory {
  public static final ExtensionPointName<FindUsagesHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.findUsagesHandlerFactory");

  public abstract boolean canFindUsages(PsiElement element);

  @Nullable
  public abstract FindUsagesHandler createFindUsagesHandler(PsiElement element, final boolean forHighlightUsages);
}
