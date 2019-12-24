// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A reference to a {@link Symbol} or possibly several Symbols.
 * <p/>
 * SymbolReference may be backed by a {@link PsiElement} but is not required to.
 * <p/>
 * Examples:
 * <ul>
 * <li>the variable name used in an expression is a SymbolReference backed by a PsiElement which represent the expression</li>
 * <li>fully qualified name of a class in Application or Test Run Configuration is a SymbolReference which is not backed by a PsiElement,
 * but may be searched for and/or renamed</li>
 * </ul>
 *
 * @see com.intellij.model
 * @see com.intellij.model.psi.PsiSymbolReference
 */
public interface SymbolReference {

  /**
   * @return collection of referenced symbols with additional data, or empty collection if there are no targets
   */
  @NotNull
  Collection<? extends SymbolResolveResult> resolveReference();

  /**
   * Default implementation checks results from {@link #resolveReference()}.
   * Override this method to skip actual resolution if this reference cannot ever resolve to this target.
   *
   * @return whether this reference resolves to a target
   */
  default boolean resolvesTo(@NotNull Symbol target) {
    return ContainerUtil.or(resolveReference(), it -> it.getTarget().equals(target));
  }
}
