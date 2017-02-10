/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class JavaProjectSdkSetupValidator implements ProjectSdkSetupValidator {
  public static final JavaProjectSdkSetupValidator INSTANCE = new JavaProjectSdkSetupValidator();
  @Override
  public boolean isApplicableFor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getFileType() != JavaClassFileType.INSTANCE) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        return psiFile.getLanguage().isKindOf(JavaLanguage.INSTANCE);
      }
    }
    return false;
  }

  @Nullable
  @Override
  public String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null && !module.isDisposed()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null) {
        if (ModuleRootManager.getInstance(module).isSdkInherited()) {
          return ProjectBundle.message("project.sdk.not.defined");
        }
        else {
          return ProjectBundle.message("module.sdk.not.defined");
        }
      }
    }
    return null;
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull VirtualFile file) {
    final Sdk projectSdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk();
    if (projectSdk != null) {
      final Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module != null) {
        WriteAction.run(() -> ModuleRootModificationUtil.setSdkInherited(module));
      }
    }
  }
}
