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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class NullFileReferenceHelper extends FileReferenceHelper {

  public static final NullFileReferenceHelper INSTANCE = new NullFileReferenceHelper();

  @Override
  public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile contentRootForFile = index.getContentRootForFile(file);

    return contentRootForFile != null ? PsiManager.getInstance(project).findDirectory(contentRootForFile) : null;
  }

  @Override
  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
    return ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getContentRoots(), new Function<VirtualFile, PsiFileSystemItem>() {
      @Override
      public PsiFileSystemItem fun(VirtualFile virtualFile) {
        return PsiManager.getInstance(module.getProject()).findDirectory(virtualFile);
      }
    });
  }

  @Override
  @NotNull
  public Collection<PsiFileSystemItem> getContexts(final Project project, @NotNull final VirtualFile file) {
    final PsiFileSystemItem item = getPsiFileSystemItem(project, file);
    if (item != null) {
      final PsiFileSystemItem parent = item.getParent();
      if (parent != null) {
        return Collections.singleton(parent);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isMine(final Project project, @NotNull final VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().isInContent(file);
  }

  @Override
  public boolean isFallback() {
    return true;
  }
}
