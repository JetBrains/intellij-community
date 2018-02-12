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

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

public interface PackageSet {
  boolean contains(@NotNull PsiFile file, NamedScopesHolder holder);
  @NotNull
  PackageSet createCopy();
  @NonNls @NotNull
  String getText();
  int getNodePriority();

  /**
   * Applies given {@code transformation} to all inner {@code PackageSet} of this instance and returns the modified instance or {@code this}
   * if no modification in inner {@code PackageSet} were made.
   */
  default PackageSet map(Function<PackageSet, PackageSet> transformation) {
    return transformation.apply(this);
  }

  /**
   * @return {@code true} if any inner {@code PackageSet} of this instance matched given predicate
   */
  default boolean anyMatches(Predicate<PackageSet> predicate) {
    return predicate.test(this);
  }
}