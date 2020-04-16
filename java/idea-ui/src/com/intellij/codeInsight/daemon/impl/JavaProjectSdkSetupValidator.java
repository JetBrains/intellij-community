// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.SdkPopupBuilder;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
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
    return JavaSdk.getInstance().isRelevantForFile(project, file);
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

      if (sdk.getSdkType().equals(JavaSdk.getInstance()) && !SdkDownloadTracker.getInstance().isDownloading(sdk) && !DumbService.getInstance(project).isDumb()) {
        boolean isJdkBroken = JavaPsiFacade
                                .getInstance(project)
                                .findClass(CommonClassNames.JAVA_LANG_OBJECT, module.getModuleWithLibrariesScope()) == null;

        if (isJdkBroken) {
          if (ModuleRootManager.getInstance(module).isSdkInherited()) {
            return JavaUiBundle.message("project.sdk.not.valid", sdk.getName());
          }
          else {
            return JavaUiBundle.message("module.sdk.not.valid", sdk.getName());
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static SdkPopupBuilder preparePopup(@NotNull Project project, @NotNull VirtualFile file) {
    SdkPopupBuilder builder = SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType);

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null && !module.isDisposed()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        //filter probably broken SDK
        builder = builder.withSdkFilter(it -> !it.getName().equalsIgnoreCase(sdk.getName()));
      }
    }

    return builder.updateSdkForFile(file);
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
