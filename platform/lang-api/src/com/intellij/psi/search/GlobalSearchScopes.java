/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class GlobalSearchScopes extends GlobalSearchScopesCore {
  private GlobalSearchScopes() {
  }

  @NotNull
  public static SearchScope openFilesScope(@NotNull Project project) {
    final VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
    // We can not use GlobalSearchScope as it is usually bound to project and used via filtering of project / library roots
    // Open files can be out of project roots e.g. arbitrary open file so we go LocalSearchScope way, filled with elements to process
    List<PsiElement> psiFiles = new ArrayList<PsiElement>(files.length);
    PsiManager psiManager = PsiManager.getInstance(project);
    for(VirtualFile file:files) {
      psiFiles.add(psiManager.findFile(file));
    }
    return new LocalSearchScope(psiFiles.toArray(new PsiElement[psiFiles.size()]), "Open Files");
  }
}
