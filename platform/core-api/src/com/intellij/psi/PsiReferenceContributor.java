// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;

/**
 * Allows providing references ({@link PsiReference}) to supporting PSI elements.
 * <p>
 * Such known elements include XML tag and attribute values, Java/Python/Javascript
 * literal expressions, comments, etc.
 * <p>
 * Register via extension point {@code com.intellij.psi.referenceContributor}.
 * <p>
 * The reference contributors are run once per project and are able to
 * register reference providers for specific locations. See {@link PsiReferenceRegistrar} for more details.
 * <p>
 * The contributed references may then be obtained via
 * {@link PsiReferenceService#getReferences(PsiElement, PsiReferenceService.Hints)},
 * which is the preferred way.
 * Some elements return them from {@link PsiElement#getReferences()} directly, though, but one should not rely on that
 * behavior since it may be changed in the future.
 * <p>
 * Note that, if you're implementing a custom language, it won't by default support references registered through PsiReferenceContributor.
 * If you want to support that, you need to call
 * {@link com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry#getReferencesFromProviders(PsiElement)} from your implementation
 * of {@code PsiElement.getReferences()}.
 * <p>
 * The alternative way to register {@link PsiReferenceProvider} is by using {@link PsiReferenceProviderBean}.
 *
 * @see PsiReferenceProviderBean
 */
public abstract class PsiReferenceContributor implements Disposable {

  public static final ExtensionPointName<KeyedLazyInstance<PsiReferenceContributor>> EP_NAME =
    ExtensionPointName.create("com.intellij.psi.referenceContributor");

  public abstract void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar);

  @Override
  public void dispose() {
  }
}
