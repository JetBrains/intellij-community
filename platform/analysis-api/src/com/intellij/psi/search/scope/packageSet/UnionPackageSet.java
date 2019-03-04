/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class UnionPackageSet extends CompoundPackageSet {
  @NotNull
  public static PackageSet create(@NotNull PackageSet... sets) {
    if (sets.length == 0) throw new IllegalArgumentException("empty arguments");
    return sets.length == 1 ? sets[0] : new UnionPackageSet(sets);
  }

  /**
   * @deprecated use {@link #create(PackageSet...)} instead
   */
  @Deprecated
  public UnionPackageSet(@NotNull PackageSet set1, @NotNull PackageSet set2) {
    super(set1, set2);
  }

  private UnionPackageSet(@NotNull PackageSet... sets) {
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