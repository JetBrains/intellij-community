// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.SdkPopupBuilder;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel.ActionHandler;
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
          return JavaUiBundle.message("project.sdk.not.defined");
        }
        else {
          return JavaUiBundle.message("module.sdk.not.defined");
        }
      }
    }
    return null;
  }

  @NotNull
  private static SdkPopupBuilder preparePopup(@NotNull Project project, @NotNull VirtualFile file) {
    return SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType)
      .updateSdkForFile(file);
  }

  @NotNull
  @Override
  public ActionHandler getFixHandler(@NotNull Project project, @NotNull VirtualFile file) {
    return preparePopup(project, file).buildEditorNotificationPanelHandler();
  }

  /**
   * @deprecated use {@link #getFixHandler(Project, VirtualFile)} instead
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void doFix(@NotNull Project project, @NotNull VirtualFile file) {
    //implemented for backward compatibility with the older code
    preparePopup(project, file).buildPopup().showInFocusCenter();
  }
}
