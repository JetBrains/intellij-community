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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author peter
 */
public abstract class FileReferenceHelper {

  public static final ExtensionPointName<FileReferenceHelper> EP_NAME = new ExtensionPointName<>("com.intellij.psi.fileReferenceHelper");

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url;
  }

  @NotNull
  public List<? extends LocalQuickFix> registerFixes(FileReference reference) {
    return emptyList();
  }

  @Nullable
  public PsiFileSystemItem getPsiFileSystemItem(final Project project, @NotNull final VirtualFile file) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return getPsiFileSystemItem(psiManager, file);
  }

  public static PsiFileSystemItem getPsiFileSystemItem(PsiManager psiManager, VirtualFile file) {
    return file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
  }

  @Nullable
  public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
    return null;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    return emptyList();
  }

  @NotNull
  public abstract Collection<PsiFileSystemItem> getContexts(final Project project, @NotNull final VirtualFile file);

  public abstract boolean isMine(final Project project, @NotNull final VirtualFile file);

  public boolean isFallback() {
    return false;
  }

  /**
   * Provides file target contexts, locations where users can create a file, depending on passed {@code file}.
   */
  @NotNull
  public Collection<FileTargetContext> getTargetContexts(@NotNull Project project, @NotNull VirtualFile file, boolean isAbsoluteReference) {
    if (isAbsoluteReference) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = index.getModuleForFile(file);
      if (module == null) {
        return emptyList();
      }
      return ContainerUtil.map(getRoots(module), FileTargetContext::new);
    }

    return ContainerUtil.map(getContexts(project, file), FileTargetContext::new);
  }
}
