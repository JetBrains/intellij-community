/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

public abstract class SearchScope {
  private static int hashCodeCounter;

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private final int myHashCode = hashCodeCounter++;

  /**
   * Overridden for performance reason. Object.hashCode() is native method and becomes a bottleneck when called often.
   *
   * @return hashCode value semantically identical to one from Object but not native
   */
  @Override
  public int hashCode() {
    return myHashCode;
  }

  @NotNull
  public String getDisplayName() {
    return PsiBundle.message("search.scope.unknown");
  }

  @NotNull public abstract SearchScope intersectWith(@NotNull SearchScope scope2);
  @NotNull public abstract SearchScope union(@NotNull SearchScope scope);

  public abstract boolean contains(@NotNull VirtualFile file);
}
