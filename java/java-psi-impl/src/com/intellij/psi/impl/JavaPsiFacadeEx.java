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

/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.Callable;

public abstract class JavaPsiFacadeEx extends JavaPsiFacade {
  @TestOnly
  public static JavaPsiFacadeEx getInstanceEx(@NotNull Project project) {
    return (JavaPsiFacadeEx)getInstance(project);
  }

  /**
   * Executes the given callable within a temporary scope where caches for scopes are temporary.
   * It can be necessary to work with file copies not to preserve a lot of unnecessary scopes,
   * because it can be memory-consuming
   * This method uses thread local variables to keep it state.
   *
   * @param <T> the type of the result returned by the callable
   * @param callable the callable to be executed within the temporary scope
   * @return the result produced by the callable
   */
  @ApiStatus.Internal
  public abstract <T> T withTemporaryScopeCaches(Callable<T> callable);

  /**
   * @return true if temporary scope caches are enabled, otherwise false
   *
   * @see #withTemporaryScopeCaches(Callable)
   */
  @ApiStatus.Internal
  public abstract boolean temporaryScopeCachesEnabled();

  @TestOnly
  public PsiClass findClass(@NotNull String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.allScope(getProject()));
  }
}