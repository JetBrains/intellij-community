// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
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
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        return psiFile.getLanguage().isKindOf(JavaLanguage.INSTANCE);
      }
    }
    return false;
  }

  @Override
  public @Nullable String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
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
      else if (sdk.getSdkType().equals(JavaSdk.getInstance()) && sdk.getRootProvider().getFiles(OrderRootType.CLASSES).length == 0) {
        return JavaUiBundle.message("project.or.module.jdk.misconfigured", ModuleRootManager.getInstance(module).isSdkInherited() ? 0 : 1);
      }
    }
    else if (ScratchUtil.isScratch(file) && ProjectRootManager.getInstance(project).getProjectSdk() == null) {
      return JavaUiBundle.message("project.sdk.not.defined");
    }
    return null;
  }

  private static @NotNull SdkPopupBuilder preparePopup(@NotNull Project project, @NotNull VirtualFile file) {
    return SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType)
      .updateSdkForFile(file);
  }

  @Override
  public @NotNull ActionHandler getFixHandler(@NotNull Project project, @NotNull VirtualFile file) {
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
