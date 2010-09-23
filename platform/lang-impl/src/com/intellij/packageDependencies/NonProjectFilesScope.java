/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packageDependencies;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.Colored;
import org.intellij.lang.annotations.RegExp;

/**
 * @author Konstantin Bulenkov
 */
@Colored(color = NonProjectFilesScope.DEFAULT_COLOR)
public class NonProjectFilesScope extends NamedScope {
  public static final String NAME = "Non-Project Files";
  @RegExp(prefix = "[0-9a-f]{6}")
  public static final String DEFAULT_COLOR = "ffffe4";

  public NonProjectFilesScope(final Project project) {
    super(NAME, new PackageSet() {
        public boolean contains(PsiFile psiFile, NamedScopesHolder holder) {
          final VirtualFile file = psiFile.getVirtualFile();
          if (file == null) return true;
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          return !(project.isInitialized() && !fileIndex.isIgnored(file) && fileIndex.getContentRootForFile(file) != null);
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return "NonProject";
        }

        public int getNodePriority() {
          return 0;
        }
      });
  }
}
