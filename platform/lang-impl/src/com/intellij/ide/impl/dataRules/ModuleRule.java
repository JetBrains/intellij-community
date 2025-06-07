// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.LangDataKeys.*;

final class ModuleRule {
  static @Nullable Module getData(@NotNull DataMap dataProvider) {
    Module moduleContext = dataProvider.get(MODULE_CONTEXT);
    if (moduleContext != null) {
      return moduleContext;
    }
    Project project = extractProject(dataProvider);
    if (project == null) return null;

    VirtualFile[] files = dataProvider.get(VIRTUAL_FILE_ARRAY);
    if (files == null) {
      files = VirtualFileArrayRule.getData(dataProvider);
    }

    if (files == null) {
      return null;
    }

    Module singleModule = null;
    for (VirtualFile file : files) {
      Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null) {
        return null;
      }
      if (singleModule == null) {
        singleModule = module;
      }
      else if (module != singleModule) {
        return null;
      }
    }

    return singleModule;
  }

  private static @Nullable Project extractProject(@NotNull DataMap dataProvider) {
    Project project = dataProvider.get(PROJECT);
    if (project != null) return project;
    
    Editor editor = dataProvider.get(EDITOR);
    project = editor != null ? editor.getProject() : null;
    if (project != null) return project;
    PsiFile file = dataProvider.get(PSI_FILE);
    if (file != null && file.isValid()) return file.getProject();

    //todo remove this part, it can be too slow for such simple rule
    PsiElement element = dataProvider.get(PSI_ELEMENT);
    if (element == null) {
      PsiElement[] psiElements = dataProvider.get(PSI_ELEMENT_ARRAY);
      if (psiElements != null && psiElements.length > 0) {
        element = psiElements[0];
      }
    }
    if (element == null || !element.isValid()) return null;
    return element.getProject();
  }
}