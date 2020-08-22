// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class IntersectionPackageSet extends CompoundPackageSet {
  @NotNull
  public static PackageSet create(PackageSet @NotNull ... sets) {
    if (sets.length == 0) throw new IllegalArgumentException("empty arguments");
    return sets.length == 1 ? sets[0] : new IntersectionPackageSet(sets);
  }

  private IntersectionPackageSet(PackageSet @NotNull ... sets) {
    super(sets);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    PsiFile psiFile = null;

    for (PackageSet set : mySets) {
      if (set instanceof PackageSetBase) {
        if (!((PackageSetBase)set).contains(file, project, holder)) {
          return false;
        }
      }
      else {
        if (psiFile == null) {
          psiFile = getPsiFile(file, project);
        }
        if (!set.contains(psiFile, holder)) return false;
      }
    }
    return true;
  }

  @Override
  public int getNodePriority() {
    return 2;
  }

  @Override
  public PackageSet map(@NotNull Function<? super PackageSet, ? extends PackageSet> transformation) {
    return create(ContainerUtil.map(mySets, s -> transformation.apply(s), new PackageSet[mySets.length]));
  }

  @Override
  @NotNull
  public String getText() {
    if (myText == null) {
      myText = StringUtil.join(mySets, set -> {
        boolean needParen = set.getNodePriority() > getNodePriority();
        return (needParen ? "(" : "") + set.getText() + (needParen ? ")" : "");
      }, "&&");
    }
    return myText;
  }
}
