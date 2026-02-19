// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class PatternPackageReferenceSet extends PackageReferenceSet {

  public PatternPackageReferenceSet(@NotNull String packageName,
                                    @NotNull PsiElement element,
                                    int startInElement,
                                    @NotNull GlobalSearchScope scope) {
    super(packageName, element, startInElement, scope);
  }

  @Override
  public @Unmodifiable Collection<PsiPackage> resolvePackageName(final @Nullable PsiPackage context, final String packageName) {
    if (context == null) return Collections.emptySet();

    if (packageName.contains("*")) {
      final Set<PsiPackage> packages = new LinkedHashSet<>();
      final Pattern pattern = PatternUtil.fromMask(packageName);
      processSubPackages(context, psiPackage -> {
        String name = psiPackage.getName();
        if (name != null && pattern.matcher(name).matches()) {
          packages.add(psiPackage);
        }
        return true;
      });

      return packages;
    }

    return super.resolvePackageName(context, packageName);
  }

  protected boolean processSubPackages(final PsiPackage pkg, final Processor<? super PsiPackage> processor) {
    for (final PsiPackage aPackage : pkg.getSubPackages(getResolveScope())) {
      if (!processor.process(aPackage)) return false;
      if (!processSubPackages(aPackage, processor)) return false;
    }
    return true;
  }
}
