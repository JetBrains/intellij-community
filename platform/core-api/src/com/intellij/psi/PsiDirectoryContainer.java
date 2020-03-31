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

package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PsiDirectoryContainer extends PsiNamedElement {
  /**
   * Returns the array of all directories (under all source roots in the project)
   * corresponding to the package.
   *
   * @return the array of directories.
   */
  PsiDirectory @NotNull [] getDirectories();

  /**
   * Returns the array of directories corresponding to the package in the specified search scope.
   *
   * @param scope the scope in which directories are searched.
   * @return the array of directories.
   */
  PsiDirectory @NotNull [] getDirectories(@NotNull GlobalSearchScope scope);
}
