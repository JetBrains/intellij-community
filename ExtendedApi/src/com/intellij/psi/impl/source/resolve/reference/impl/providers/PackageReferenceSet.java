/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class PackageReferenceSet extends ReferenceSetBase<PsiPackageReference> {

  public PackageReferenceSet(@NotNull final String str, @NotNull final PsiElement element, final int startInElement) {
    super(str, element, startInElement, DOT_SEPARATOR);
  }

  @NotNull
  protected PsiPackageReference createReference(final TextRange range, final int index) {
    return new PsiPackageReference(this, range, index);
  }

  public Collection<PsiPackage> resolvePackageName(PsiPackage context, String packageName) {
    for (PsiPackage aPackage : context.getSubPackages()) {
      if (Comparing.equal(aPackage.getName(), packageName)) {
        return Collections.singleton(aPackage);
      }
    }
    return Collections.emptyList();
  }

  public Collection<PsiPackage> resolvePackage() {
    final PsiPackageReference packageReference = getLastReference();
    if (packageReference == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map2List(packageReference.multiResolve(false), new NullableFunction<ResolveResult, PsiPackage>() {
      public PsiPackage fun(final ResolveResult resolveResult) {
        return (PsiPackage)resolveResult.getElement();
      }
    });
  }

}