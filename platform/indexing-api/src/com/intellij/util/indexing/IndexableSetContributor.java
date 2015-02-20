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
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class IndexableSetContributor implements IndexedRootsProvider {
  
  protected static final Set<VirtualFile> EMPTY_FILE_SET = Collections.unmodifiableSet(new HashSet<VirtualFile>());
  
  @Override
  public final Set<String> getRootsToIndex() {
    return ContainerUtil.map2Set(getAdditionalRootsToIndex(), new NotNullFunction<VirtualFile, String>() {
      @NotNull
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getUrl();
      }
    });
  }

  @NotNull
  public static Set<VirtualFile> getProjectRootsToIndex(IndexedRootsProvider provider, Project project) {
    if (provider instanceof IndexableSetContributor) {
      return ((IndexableSetContributor)provider).getAdditionalProjectRootsToIndex(project);
    }
    return EMPTY_FILE_SET;
  }

  public static Set<VirtualFile> getRootsToIndex(IndexedRootsProvider provider) {
    if (provider instanceof IndexableSetContributor) {
      return ((IndexableSetContributor)provider).getAdditionalRootsToIndex();
    }

    final HashSet<VirtualFile> result = new HashSet<VirtualFile>();
    for (String url : provider.getRootsToIndex()) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }

    return result;
  }

  @NotNull
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@Nullable Project project) {
    return EMPTY_FILE_SET;
  }

  public abstract Set<VirtualFile> getAdditionalRootsToIndex();
}
