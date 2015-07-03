/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilterEx.LocationInFile;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class GeneratedSourcesFilter {
  public static final ExtensionPointName<GeneratedSourcesFilter> EP_NAME = ExtensionPointName.create("com.intellij.generatedSourcesFilter");

  public static boolean isGeneratedSourceByAnyFilter(@NotNull VirtualFile file, @NotNull Project project) {
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter.isGeneratedSource(file, project)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static List<? extends PsiElement> getAllOriginalElements(@NotNull PsiElement element) {
    List<PsiElement> result = null;
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter instanceof GeneratedSourcesFilterEx) {
        result = addAll(((GeneratedSourcesFilterEx)filter).getOriginalElements(element), result);
      }
    }
    return orEmpty(result);
  }

  @NotNull
  public static List<? extends PsiElement> getAllGeneratedElements(@NotNull PsiElement element) {
    List<PsiElement> result = null;
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter instanceof GeneratedSourcesFilterEx) {
        result = addAll(((GeneratedSourcesFilterEx)filter).getGeneratedElements(element), result);
      }
    }
    return orEmpty(result);
  }

  @NotNull
  public static List<? extends LocationInFile> getAllOriginalLocations(@NotNull Project project,
                                                                       @NotNull VirtualFile file,
                                                                       @Nullable Segment segment) {
    List<GeneratedSourcesFilterEx.LocationInFile> result = null;
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter instanceof GeneratedSourcesFilterEx) {
        result = addAll(((GeneratedSourcesFilterEx)filter).getOriginalLocations(project, file, segment), result);
      }
    }
    return orEmpty(result);
  }

  @NotNull
  public static List<? extends LocationInFile> getAllGeneratedLocations(@NotNull Project project,
                                                                        @NotNull VirtualFile file,
                                                                        @Nullable Segment segment) {
    List<GeneratedSourcesFilterEx.LocationInFile> result = null;
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter instanceof GeneratedSourcesFilterEx) {
        result = addAll(((GeneratedSourcesFilterEx)filter).getGeneratedLocations(project, file, segment), result);
      }
    }
    return orEmpty(result);
  }

  private static <T> List<T> addAll(List<? extends T> elements, List<T> result) {
    if (result == null) {
      return ContainerUtil.newArrayList(elements);
    }
    result.addAll(elements);
    return result;
  }

  private static <T> List<T> orEmpty(List<T> elements) {
    if (elements != null) return elements;
    return Collections.emptyList();
  }

  public abstract boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project);
}
