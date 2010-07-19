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

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class VirtualFileArrayRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    // Try to detect multiselection.

    Project project = PlatformDataKeys.PROJECT_CONTEXT.getData(dataProvider);
    if (project != null && !project.isDisposed()) {
      return ProjectRootManager.getInstance(project).getContentRoots();
    }

    Module[] selectedModules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataProvider);
    if (selectedModules != null && selectedModules.length > 0) {
      return getFilesFromModules(selectedModules);
    }

    Module selectedModule = LangDataKeys.MODULE_CONTEXT.getData(dataProvider);
    if (selectedModule != null && !selectedModule.isDisposed()) {
      return ModuleRootManager.getInstance(selectedModule).getContentRoots();
    }

    PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataProvider);
    if (psiElements != null && psiElements.length != 0) {
      return getFilesFromPsiElements(psiElements);
    }

    // VirtualFile -> VirtualFile[]
    VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataProvider);
    if (vFile != null) {
      return new VirtualFile[]{vFile};
    }

    //

    PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataProvider);
    if (psiFile != null && psiFile.getVirtualFile() != null) {
      return new VirtualFile[]{psiFile.getVirtualFile()};
    }

    PsiElement elem = LangDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (elem != null) {
      return getFilesFromPsiElement(elem);
    }

    Usage[] usages = UsageView.USAGES_KEY.getData(dataProvider);
    UsageTarget[] usageTargets = UsageView.USAGE_TARGETS_KEY.getData(dataProvider);
    if (usages != null || usageTargets != null)  {
      return getFilesFromUsages(usages, usageTargets);
    }


    return null;
  }

  private static VirtualFile[] getFilesFromUsages(Usage[] usages, UsageTarget[] usageTargets) {
    Set<VirtualFile> result = new HashSet<VirtualFile>();

    if (usages != null) {
      for (Usage usage : usages) {
        if (!usage.isValid()) continue;
        if (usage instanceof UsageInFile) {
          UsageInFile usageInFile = (UsageInFile)usage;
          result.add(usageInFile.getFile());
        }

        if (usage instanceof UsageInFiles) {
          UsageInFiles usageInFiles = (UsageInFiles)usage;
          ContainerUtil.addAll(result, usageInFiles.getFiles());
        }
      }
    }

    if (usageTargets != null) {
      for (UsageTarget usageTarget : usageTargets) {
        if (!usageTarget.isValid()) continue;
        VirtualFile[] files = usageTarget.getFiles();
        if (files != null) {
          ContainerUtil.addAll(result, files);
        }
      }
    }

    return VfsUtil.toVirtualFileArray(result);
  }

  private static Object getFilesFromPsiElement(PsiElement elem) {
    if (elem instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)elem).getVirtualFile();
      return virtualFile != null ? new VirtualFile[]{virtualFile} : null;
    }
    else if (elem instanceof PsiDirectory) {
      return new VirtualFile[]{((PsiDirectory)elem).getVirtualFile()};
    }
    else {
      PsiFile file = elem.getContainingFile();
      return file != null && file.getVirtualFile() != null ? new VirtualFile[]{file.getVirtualFile()} : null;
    }
  }

  private static Object getFilesFromPsiElements(PsiElement[] psiElements) {
    HashSet<VirtualFile> files = new HashSet<VirtualFile>();
    for (PsiElement elem : psiElements) {
      if (elem instanceof PsiDirectory) {
        files.add(((PsiDirectory)elem).getVirtualFile());
      }
      else if (elem instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)elem).getVirtualFile();
        if (virtualFile != null) {
          files.add(virtualFile);
        }
      }
      else if (elem instanceof PsiDirectoryContainer) {
        PsiDirectory[] dirs = ((PsiDirectoryContainer)elem).getDirectories();
        for (PsiDirectory dir : dirs) {
          files.add(dir.getVirtualFile());
        }
      }
      else {
        PsiFile file = elem.getContainingFile();
        if (file != null) {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            files.add(virtualFile);
          }
        }
      }
    }
    VirtualFile[] result = VfsUtil.toVirtualFileArray(files);
    files.clear();
    return result;
  }

  private static Object getFilesFromModules(Module[] selectedModules) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module selectedModule : selectedModules) {
      ContainerUtil.addAll(result, ModuleRootManager.getInstance(selectedModule).getContentRoots());
    }
    return VfsUtil.toVirtualFileArray(result);
  }
}
