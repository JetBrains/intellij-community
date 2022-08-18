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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
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

/**
 * @author Eugene Zhuravlev
 */
public class ModuleRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataProvider);
    if (moduleContext != null) {
      return moduleContext;
    }
    Project project = extractProject(dataProvider);
    if (project == null) return null;

    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataProvider);
    if (files == null) {
      files = (VirtualFile[])new VirtualFileArrayRule().getData(dataProvider);
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

  @Nullable
  private static Project extractProject(@NotNull DataProvider dataProvider) {
    Project project = CommonDataKeys.PROJECT.getData(dataProvider);
    if (project != null) return project;
    
    Editor editor = EDITOR.getData(dataProvider);
    project = editor != null ? editor.getProject() : null;
    if (project != null) return project;
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataProvider);
    if (file != null && file.isValid()) return file.getProject();

    //todo remove this part, it can be too slow for such simple rule
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (element == null) {
      PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataProvider);
      if (psiElements != null && psiElements.length > 0) {
        element = psiElements[0];
      }
    }
    if (element == null || !element.isValid()) return null;
    return element.getProject();
  }
}