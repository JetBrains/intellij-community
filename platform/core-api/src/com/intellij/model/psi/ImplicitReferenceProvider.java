// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

/**
 * @see com.intellij.navigation.DirectNavigationProvider
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/declarations-and-references.html">Declarations and Reference (IntelliJ Platform Docs)</a>
 */
public interface ImplicitReferenceProvider {

  @Internal
  ExtensionPointName<ImplicitReferenceProvider> EP_NAME = ExtensionPointName.create("com.intellij.psi.implicitReferenceProvider");

  /**
   * Implement this method to support {@link Symbol}-based actions.
   * <p/>
   * Non-null value enables various actions accessible on a referenced Symbol,
   * for example, navigation and link highlighting on hover.
   * Such "reference" won't be found (or renamed, etc.).
   * <p/>
   * This method is called for each element in the PSI tree
   * starting from the leaf element at the caret offset up to the file.
   */
  default @Nullable PsiSymbolReference getImplicitReference(@NotNull PsiElement element, int offsetInElement) {
    Collection<? extends Symbol> targets = resolveAsReference(element);
    return targets.isEmpty() ? null : new ImmediatePsiSymbolReference(element, targets);
  }

  /**
   * Implement this method to support {@link Symbol}-based actions.
   * <p/>
   * If this method returns a non-empty collection, then the element is treated as a reference,
   * enabling various actions accessible on a referenced Symbol,
   * for example, navigation and link highlighting on hover.
   * Such "reference" won't be found (or renamed, etc.).
   * <p/>
   * This method is called for each element in the PSI tree
   * starting from the leaf element at the caret offset up to the file.
   * <p/>
   * This method is a shortcut, it's called only from the default implementation of {@link #getImplicitReference}.
   */
  default @NotNull @Unmodifiable Collection<? extends @NotNull Symbol> resolveAsReference(@NotNull PsiElement element) {
    return Collections.emptyList();
  }
}
