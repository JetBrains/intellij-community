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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public class FileReferenceHelperRegistrar {

  private FileReferenceHelperRegistrar() {
  }

  public static FileReferenceHelper[] getHelpers() {
    return Extensions.getExtensions(FileReferenceHelper.EP_NAME);
  }

  @NotNull
  public static <T extends PsiFileSystemItem> FileReferenceHelper getNotNullHelper(@NotNull T psiFileSystemItem) {
    final FileReferenceHelper helper = getHelper(psiFileSystemItem);
    return helper == null ? new NullFileReferenceHelper() : helper;
  }

  @Nullable
  public static <T extends PsiFileSystemItem> FileReferenceHelper getHelper(@NotNull final T psiFileSystemItem) {
    final VirtualFile file = psiFileSystemItem.getVirtualFile();
    if (file == null) return null;
    final Project project = psiFileSystemItem.getProject();
    return ContainerUtil.find(getHelpers(), new Condition<FileReferenceHelper>() {
      public boolean value(final FileReferenceHelper fileReferenceHelper) {
        return fileReferenceHelper.isMine(project, file);
      }
    });
  }

  public static boolean areElementsEquivalent(@NotNull final PsiFileSystemItem element1, @NotNull final PsiFileSystemItem element2) {
    return element2.getManager().areElementsEquivalent(element1, element2);
  }

  private static class NullFileReferenceHelper extends FileReferenceHelper {

    public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      VirtualFile contentRootForFile = index.getContentRootForFile(file);

      return contentRootForFile != null ? PsiManager.getInstance(project).findDirectory(contentRootForFile) : null;
    }

    @NotNull
    public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
      return ContainerUtil.map(ModuleRootManager.getInstance(module).getContentRoots(), new Function<VirtualFile, PsiFileSystemItem>() {
        public PsiFileSystemItem fun(VirtualFile virtualFile) {
          return PsiManager.getInstance(module.getProject()).findDirectory(virtualFile);
        }
      });
    }

    @NotNull
    public Collection<PsiFileSystemItem> getContexts(final Project project, final @NotNull VirtualFile file) {
      final PsiFileSystemItem item = getPsiFileSystemItem(project, file);
      if (item != null) {
        final PsiFileSystemItem parent = item.getParent();
        if (parent != null) {
          return Collections.singleton(parent);
        }
      }
      return Collections.emptyList();
    }

    public boolean isMine(final Project project, final @NotNull VirtualFile file) {
      return ProjectRootManager.getInstance(project).getFileIndex().isInContent(file);
    }
  }
}
