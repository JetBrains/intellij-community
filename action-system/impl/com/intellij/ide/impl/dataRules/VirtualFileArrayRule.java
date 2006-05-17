package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VirtualFileArrayRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    // Try to detect multiselection.

    Project project = (Project)dataProvider.getData(DataConstants.PROJECT_CONTEXT);
    if (project != null) {
      return ProjectRootManager.getInstance(project).getContentRoots();
    }

    Module[] selectedModules = (Module[])dataProvider.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (selectedModules != null && selectedModules.length > 0) {
      return getFilesFromModules(selectedModules);
    }

    Module selectedModule = (Module)dataProvider.getData(DataConstants.MODULE_CONTEXT);
    if (selectedModule != null) {
      return ModuleRootManager.getInstance(selectedModule).getContentRoots();
    }

    PsiElement[] psiElements = (PsiElement[])dataProvider.getData(DataConstants.PSI_ELEMENT_ARRAY);
    if (psiElements != null && psiElements.length != 0) {
      return getFilesFromPsiElements(psiElements);
    }

    // VirtualFile -> VirtualFile[]
    VirtualFile vFile = (VirtualFile)dataProvider.getData(DataConstants.VIRTUAL_FILE);
    if (vFile != null) {
      return new VirtualFile[]{vFile};
    }

    //

    PsiFile psiFile = (PsiFile)dataProvider.getData(DataConstants.PSI_FILE);
    if (psiFile != null && psiFile.getVirtualFile() != null) {
      return new VirtualFile[]{psiFile.getVirtualFile()};
    }

    PsiElement elem = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (elem != null) {
      return getFilesFromPsiElement(elem);
    }

    Usage[] usages = (Usage[])dataProvider.getData(UsageView.USAGES);
    UsageTarget[] usageTargets = (UsageTarget[])dataProvider.getData(UsageView.USAGE_TARGETS);
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
          result.addAll(Arrays.asList(usageInFiles.getFiles()));
        }
      }
    }

    if (usageTargets != null) {
      for (UsageTarget usageTarget : usageTargets) {
        if (!usageTarget.isValid()) continue;
        VirtualFile[] files = usageTarget.getFiles();
        if (files != null) {
          result.addAll(Arrays.asList(files));
        }
      }
    }

    return result.toArray(new VirtualFile[result.size()]);
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
      else if (elem instanceof PsiPackage) {
        PsiDirectory[] dirs = ((PsiPackage)elem).getDirectories();
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
    VirtualFile[] result = files.toArray(new VirtualFile[files.size()]);
    files.clear();
    return result;
  }

  private static Object getFilesFromModules(Module[] selectedModules) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module selectedModule : selectedModules) {
      result.addAll(Arrays.asList(ModuleRootManager.getInstance(selectedModule).getContentRoots()));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }
}
