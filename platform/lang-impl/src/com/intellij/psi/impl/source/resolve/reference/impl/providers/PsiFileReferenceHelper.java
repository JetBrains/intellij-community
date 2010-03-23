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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class PsiFileReferenceHelper extends FileReferenceHelper {

  public List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference) {
    return FileReferenceQuickFixProvider.registerQuickFix(info, reference);
  }

  public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile contentRootForFile = index.getSourceRootForFile(file);
    if (contentRootForFile == null) contentRootForFile = index.getContentRootForFile(file);

    if (contentRootForFile != null) {
      return PsiManager.getInstance(project).findDirectory(contentRootForFile);
    }
    return null;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
    return getContextsForModule(module, "", module.getModuleWithDependenciesScope());
  }

  @NotNull
  public Collection<PsiFileSystemItem> getContexts(final Project project, @NotNull final VirtualFile file) {
    final PsiFileSystemItem item = getPsiFileSystemItem(project, file);
    if (item != null) {
      final PsiFileSystemItem parent = item.getParent();
      if (parent != null) {
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        final VirtualFile parentFile = parent.getVirtualFile();
        assert parentFile != null;
        VirtualFile root = index.getSourceRootForFile(parentFile);
        if (root != null) {
          String path = VfsUtil.getRelativePath(parentFile, root, '.');

          if (path != null) {
            final Module module = ModuleUtil.findModuleForFile(file, project);

            if (module != null) {
              OrderEntry orderEntry = ModuleRootManager.getInstance(module).getFileIndex().getOrderEntryForFile(file);

              if (orderEntry instanceof ModuleSourceOrderEntry) {
                for(ContentEntry e: ((ModuleSourceOrderEntry)orderEntry).getRootModel().getContentEntries()) {
                  for(SourceFolder sf:e.getSourceFolders()) {
                    if (sf.getFile() == root) {
                      String s = sf.getPackagePrefix();
                      if (s.length() > 0) {
                        path = s + "." + path;
                        break;
                      }
                    }
                  }
                }
              }
              return getContextsForModule(module, path, module.getModuleWithDependenciesScope());
            }
          }

          // TODO: content root
        }
        return Collections.singleton(parent);
      }
    }
    return Collections.emptyList();
  }

  public boolean isMine(final Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return index.isInSourceContent(file);
  }

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  static Collection<PsiFileSystemItem> getContextsForModule(@NotNull Module module, @NotNull String packageName, @Nullable GlobalSearchScope scope) {
    List<PsiFileSystemItem> result = null;
    Query<VirtualFile> query = DirectoryIndex.getInstance(module.getProject()).getDirectoriesByPackageName(packageName, false);
    PsiManager manager = null;

    for(VirtualFile file:query) {
      if (scope != null && !scope.contains(file)) continue;
      if (result == null) {
        result = new ArrayList<PsiFileSystemItem>();
        manager = PsiManager.getInstance(module.getProject());
      }
      PsiDirectory psiDirectory = manager.findDirectory(file);
      if (psiDirectory != null) result.add(psiDirectory);
    }

    return result != null ? result:Collections.<PsiFileSystemItem>emptyList();
  }
}
