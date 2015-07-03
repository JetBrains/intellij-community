/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Zarnekow
 */
public abstract class AbstractGeneratedSourcesFilterEx extends GeneratedSourcesFilter implements GeneratedSourcesFilterEx {

  @NotNull
  @Override
  public List<? extends PsiElement> getOriginalElements(@NotNull PsiElement element) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends PsiElement> getGeneratedElements(@NotNull PsiElement element) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends LocationInFile> getOriginalLocations(@NotNull Project project,
                                                             @NotNull VirtualFile file,
                                                             @Nullable Segment segment) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends LocationInFile> getGeneratedLocations(@NotNull Project project,
                                                              @NotNull VirtualFile file,
                                                              @Nullable Segment segment) {
    return Collections.emptyList();
  }
}
