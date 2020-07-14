// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.lang.Language;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Implement to support finding code references in your language.
 * It's okay to have several implementations per language.
 * <p/>
 * {@link PsiElement#getOwnReferences Code references} are managed by the language PSI itself.
 * Such references appear in {@link SearchContext#IN_CODE code} contexts.
 */
public interface CodeReferenceSearcher {

  ExtensionPointName<CodeReferenceSearcher> EP_NAME = ExtensionPointName.create("com.intellij.lang.codeReferenceSearcher");

  /**
   * Returned language is used to restrict effective search scope.
   */
  @NotNull Language getReferencingLanguage(@NotNull Symbol target);

  @Nullable SearchRequest getSearchRequest(@NotNull Project project, @NotNull Symbol target);

  /**
   * The <i>how</i> to search.
   * <p>
   * Given a text occurrence returns collection of found references to this target.
   * The implementations should consider PSI tree first,
   * then obtain needed references from the PSI,
   * then ask the references if they are resolved to the target.
   */
  @NotNull Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull Symbol target,
                                                                           @NotNull PsiElement scope,
                                                                           @NotNull PsiElement element,
                                                                           int offsetInElement);
}
