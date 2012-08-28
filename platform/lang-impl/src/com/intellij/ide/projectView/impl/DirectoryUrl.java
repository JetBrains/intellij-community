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

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class DirectoryUrl extends AbstractUrl {
  @NonNls private static final String ELEMENT_TYPE = "directory";

  public DirectoryUrl(String url, String moduleName) {
    super(url, moduleName,ELEMENT_TYPE);
  }
  public static DirectoryUrl create(PsiDirectory directory) {
    Project project = directory.getProject();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
    return new DirectoryUrl(virtualFile.getUrl(), module != null ? module.getName() : null);
  }

  @Override
  public Object[] createPath(final Project project) {
    if (moduleName != null) {
      final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Nullable
        @Override
        public Module compute() {
          return ModuleManager.getInstance(project).findModuleByName(moduleName);
        }
      });
      if (module == null) return null;
    }
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    final VirtualFile file = virtualFileManager.findFileByUrl(url);
    if (file == null) return null;
    final PsiDirectory directory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
      @Nullable
      @Override
      public PsiDirectory compute() {
        return PsiManager.getInstance(project).findDirectory(file);
      }
    });
    if (directory == null) return null;
    return new Object[]{directory};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new DirectoryUrl(url, moduleName);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof PsiDirectory) {
      return create((PsiDirectory)element);
    }
    return null;
  }
}
