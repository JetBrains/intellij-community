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

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public class PsiPatchBaseDirectoryDetector extends PatchBaseDirectoryDetector {
  private final Project myProject;

  public PsiPatchBaseDirectoryDetector(final Project project) {
    myProject = project;
  }

  @Override
  @Nullable
  public Result detectBaseDirectory(final String patchFileName) {
    String[] nameComponents = patchFileName.split("/");
    String patchName = nameComponents[nameComponents.length - 1];
    if (patchName.isEmpty()) {
      return null;
    }
    final PsiFile[] psiFiles = FilenameIndex.getFilesByName(myProject, patchName, GlobalSearchScope.projectScope(myProject));
    if (psiFiles.length == 1) {
      PsiDirectory parent = psiFiles [0].getContainingDirectory();
      for(int i=nameComponents.length-2; i >= 0; i--) {
        if (!parent.getName().equals(nameComponents[i]) || Comparing.equal(parent.getVirtualFile(), myProject.getBaseDir())) {
          return new Result(parent.getVirtualFile().getPresentableUrl(), i+1);
        }
        parent = parent.getParentDirectory();
      }
      if (parent == null) return null;
      return new Result(parent.getVirtualFile().getPresentableUrl(), 0);
    }
    return null;
  }

  @Override
  public Collection<VirtualFile> findFiles(final String fileName) {
    return FilenameIndex.getVirtualFilesByName(myProject, fileName, GlobalSearchScope.projectScope(myProject));
  }
}
