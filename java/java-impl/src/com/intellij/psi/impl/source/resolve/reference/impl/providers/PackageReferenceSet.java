// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class PackageReferenceSet extends ReferenceSetBase<PsiPackageReference> {
  private final GlobalSearchScope mySearchScope;

  public PackageReferenceSet(final @NotNull String str, final @NotNull PsiElement element, final int startInElement) {
    this(str, element, startInElement, element.getResolveScope());
  }

  public PackageReferenceSet(final @NotNull String str, final @NotNull PsiElement element, final int startInElement, @NotNull GlobalSearchScope scope) {
    super(str, element, startInElement, DOT_SEPARATOR);
    mySearchScope=scope;
  }

  @Override
  protected @NotNull PsiPackageReference createReference(final TextRange range, final int index) {
    return new PsiPackageReference(this, range, index);
  }

  public @Unmodifiable Collection<PsiPackage> resolvePackageName(@Nullable PsiPackage context, final String packageName) {
    if (context != null) {
      return ContainerUtil.filter(context.getSubPackages(getResolveScope()), aPackage -> Objects.equals(aPackage.getName(), packageName));
    }
    return Collections.emptyList();
  }

  protected @NotNull GlobalSearchScope getResolveScope() {
    return mySearchScope;
  }

  public @Unmodifiable Collection<PsiPackage> resolvePackage() {
    final PsiPackageReference packageReference = getLastReference();
    if (packageReference == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(packageReference.multiResolve(false),
                                  (NullableFunction<ResolveResult, PsiPackage>)resolveResult -> (PsiPackage)resolveResult.getElement());
  }

  public Set<PsiPackage> getInitialContext() {
    return Collections.singleton(JavaPsiFacade.getInstance(getElement().getProject()).findPackage(""));
  }
}