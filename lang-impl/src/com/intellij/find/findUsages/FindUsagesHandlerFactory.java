package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface FindUsagesHandlerFactory {
  boolean canFindUsages(PsiElement element);

  @Nullable
  FindUsagesHandler createFindUsagesHandler(PsiElement element);
}
