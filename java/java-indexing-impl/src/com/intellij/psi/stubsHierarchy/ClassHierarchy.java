/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class ClassHierarchy {
  /**
   * @return the list of pointers to all classes in this project that where all supertype references were resolved
   */
  @NotNull
  public abstract List<? extends SmartClassAnchor> getCoveredClasses();

  /**
   * @return the list of pointers to all classes in this project, indexed by stub hierarchy support
   */
  @NotNull
  public abstract List<? extends SmartClassAnchor> getAllClasses();

  @NotNull
  public abstract SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull PsiClass psiClass);

  @NotNull
  public abstract SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull SmartClassAnchor anchor);

  @Nullable
  public abstract SmartClassAnchor findAnchor(@NotNull PsiClass psiClass);

  /**
   * @return whether stub hierarchy resolver couldn't determine the super class exactly because there were several possible candidates
   */
  public abstract boolean hasAmbiguousSupers(@NotNull SmartClassAnchor anchor);

  public abstract boolean isAnonymous(@NotNull SmartClassAnchor anchor);

  /**
   * @return the given scope restricted to the files not covered by this hierarchy, to use usual PSI/resolve-based inheritor search in it
   */
  @NotNull
  public abstract GlobalSearchScope restrictToUncovered(@NotNull GlobalSearchScope scope);
}
