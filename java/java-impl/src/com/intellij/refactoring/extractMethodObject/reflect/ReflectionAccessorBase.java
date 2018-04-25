// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Grants access for inaccessible members using reflection. Adds an additional method per each expression
 * with inaccessible members (method calls, field references, constructor calls, etc)
 *
 * @author Vitaliy.Bibaev
 */
public abstract class ReflectionAccessorBase<T extends ItemToReplaceDescriptor> implements ReflectionAccessor {
  private final PsiClass myPsiClass;
  private final PsiElementFactory myElementFactory;
  private final Logger log = Logger.getInstance(getClass());

  protected ReflectionAccessorBase(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    myPsiClass = psiClass;
    myElementFactory = elementFactory;
  }

  @Override
  public void accessThroughReflection(@NotNull PsiElement element) {
    List<T> toReplace = findItemsToReplace(element);
    log.info("Found " + toReplace.size() + " items to replace");
    for (T item : toReplace) {
      grantAccess(item);
    }

    List<T> remaining = findItemsToReplace(element);
    if (!remaining.isEmpty()) {
      log.warn("Some inaccessible items were not replaced");
    }
  }

  @NotNull
  protected PsiElementFactory getElementFactory() {
    return myElementFactory;
  }

  @NotNull
  protected PsiClass getOuterClass() {
    return myPsiClass;
  }

  /**
   * Implementers of this method must place all child elements before their parent to ensure that all elements are not replaced
   * together a previous element
   */
  protected abstract List<T> findItemsToReplace(@NotNull PsiElement element);

  protected abstract void grantAccess(@NotNull T descriptor);
}
