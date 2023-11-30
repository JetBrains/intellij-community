// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

public final class ComplementPackageSet extends PackageSetBase {
  private final @NotNull PackageSet myComplementarySet;

  public ComplementPackageSet(@NotNull PackageSet set) {
    myComplementarySet = set;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    return myComplementarySet instanceof PackageSetBase ? !((PackageSetBase)myComplementarySet).contains(file, project, holder)
                                                        : !myComplementarySet.contains(getPsiFile(file, project), holder);
  }

  @Override
  public @NotNull PackageSet createCopy() {
    return new ComplementPackageSet(myComplementarySet.createCopy());
  }

  @Override
  public @NotNull String getText() {
    StringBuilder buf = new StringBuilder();
    boolean needParen = myComplementarySet.getNodePriority() > getNodePriority();
    buf.append('!');
    if (needParen) buf.append('(');
    buf.append(myComplementarySet.getText());
    if (needParen) buf.append(')');
    return buf.toString();
  }

  @Override
  public int getNodePriority() {
    return 1;
  }

  @Override
  public PackageSet map(@NotNull Function<? super PackageSet, ? extends PackageSet> transformation) {
    PackageSet updated = transformation.apply(myComplementarySet);
    return updated != myComplementarySet ? new ComplementPackageSet(updated) : this;
  }

  @Override
  public boolean anyMatches(@NotNull Predicate<? super PackageSet> predicate) {
    return predicate.test(myComplementarySet);
  }

  public @NotNull PackageSet getComplementarySet() {
    return myComplementarySet;
  }
}
