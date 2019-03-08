// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static org.jetbrains.annotations.ApiStatus.Experimental;

/**
 * A reference to a {@link Symbol} or possibly several Symbols.
 * <p/>
 * SymbolReference may be backed by a {@link com.intellij.psi.PsiElement PsiElement} but is not required to.
 * <p/>
 * Examples:
 * <ul>
 * <li>the variable name used in an expression is a SymbolReference backed by a PsiElement which represent the expression</li>
 * <li>fully qualified name of a class in Application or Test Run Configuration is a SymbolReference which is not backed by a PsiElement,
 * but may be searched for and/or renamed</li>
 * </ul>
 */
@Experimental
public interface SymbolReference {

  @NotNull
  Collection<? extends SymbolResolveResult> resolveReference();
}
