// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class UnionPackageSet extends CompoundPackageSet {
  public static @NotNull PackageSet create(PackageSet @NotNull ... sets) {
    if (sets.length == 0) throw new IllegalArgumentException("empty arguments");
    return sets.length == 1 ? sets[0] : new UnionPackageSet(sets);
  }

  private UnionPackageSet(PackageSet @NotNull ... sets) {
    super(sets);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    PsiFile psiFile = null;

    for (PackageSet set : mySets) {
      if (set instanceof PackageSetBase) {
        if (((PackageSetBase)set).contains(file, project, holder)) {
          return true;
        }
      }
      else {
        if (psiFile == null) {
          psiFile = getPsiFile(file, project);
        }
        if (set.contains(psiFile, holder)) return true;
      }
    }
    return false;
  }

  @Override
  public int getNodePriority() {
    return 3;
  }

  @Override
  public PackageSet map(@NotNull Function<? super PackageSet, ? extends PackageSet> transformation) {
    return create(ContainerUtil.map(mySets, s -> transformation.apply(s), new PackageSet[mySets.length]));
  }

}