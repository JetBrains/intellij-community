// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.SdkPopupBuilder;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.ActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Pavel.Dolgov
 */
public class JavaProjectSdkSetupValidator implements ProjectSdkSetupValidator {
  private static final Logger LOG = Logger.getInstance(JavaProjectSdkSetupValidator.class);

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

  private static class ContextInfo {
    final Module module;
    final Sdk sdk;

    final String sdkName;
    final boolean isSdkDownloading;
    final boolean isSdkInheritedFromProject;

    private ContextInfo(@NotNull Project project, @NotNull VirtualFile file) {
      module = ModuleUtilCore.findModuleForFile(file, project);
      sdk = module != null && !module.isDisposed() ? ModuleRootManager.getInstance(module).getSdk() : null;
      isSdkDownloading = sdk != null && SdkDownloadTracker.getInstance().isDownloading(sdk);
      isSdkInheritedFromProject = module != null && ModuleRootManager.getInstance(module).isSdkInherited();
      sdkName = sdk != null ? sdk.getName() : "";
    }

    boolean isSdkMissing() {
      return module != null && sdk == null;
    }

    boolean isUsingInvalidSdk() {
      if (module == null) return false;
      if (sdk == null || isSdkDownloading) return false;

      //we only check out SDK types (e.g. Kotlin SDK can be different)
      if (!sdk.getSdkType().equals(JavaSdk.getInstance())) return false;

      try {
        //we run a cheap test that uses VFS inside to check if JDK home exists
        return !JavaSdk.getInstance().sdkHasValidPath(sdk);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.warn("Failed to check JDK " + sdk.getName() + " with home " + sdk.getHomePath() + ". " + e.getMessage(), e);
        //we failed checking it, assume it's OK to avoid false positives
        return false;
      }
    }
  }

  @Nullable
  @Override
  public String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
    ContextInfo info = new ContextInfo(project, file);

    if (info.isSdkMissing()) {
      if (info.isSdkInheritedFromProject) {
        return JavaUiBundle.message("project.sdk.not.defined");
      }
      else {
        return JavaUiBundle.message("module.sdk.not.defined");
      }
    }

    if (info.isUsingInvalidSdk()) {
      if (info.isSdkInheritedFromProject) {
        return JavaUiBundle.message("project.sdk.not.valid", info.sdkName);
      }
      else {
        return JavaUiBundle.message("module.sdk.not.valid", info.sdkName);
      }
    }
    return null;
  }

  @Nullable
  private static SdkPopupBuilder preparePopup(@NotNull Project project, @NotNull VirtualFile file) {
    ContextInfo info = new ContextInfo(project, file);

    SdkPopupBuilder builder = SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType);

    if (info.isSdkMissing()) {
      return builder.updateSdkForFile(file);
    }

    Sdk sdk = info.sdk;
    if (info.isUsingInvalidSdk() && sdk != null) {
      return builder
        .withSdkFilter(it -> !it.getName().equalsIgnoreCase(sdk.getName()))
        .onSdkSelected( newSdk -> {
          //if the selected SDK is downloading, let's have our's updated at the end too
          SdkDownloadTracker.getInstance().registerEditableSdk(newSdk, sdk);

          WriteAction.run(() -> {
            SdkModificator mod = sdk.getSdkModificator();
            mod.setHomePath(newSdk.getHomePath());
            mod.setVersionString(newSdk.getVersionString());
            mod.commitChanges();

            if (!SdkDownloadTracker.getInstance().isDownloading(newSdk)) {
              JavaSdk.getInstance().setupSdkPaths(sdk);
            }
          });

          UnknownSdkTracker.getInstance(project).updateUnknownSdks();
        });
    }

    return null;
  }

  @NotNull
  @Override
  public ActionHandler getFixHandler(@NotNull Project project, @NotNull VirtualFile file) {
    SdkPopupBuilder builder = preparePopup(project, file);
    if (builder == null) return NOOP;
    return builder.buildEditorNotificationPanelHandler();
  }

  /**
   * @deprecated use {@link #getFixHandler(Project, VirtualFile)} instead
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void doFix(@NotNull Project project, @NotNull VirtualFile file) {
    //implemented for backward compatibility with the older code
    SdkPopupBuilder builder = preparePopup(project, file);
    if (builder == null) return;

    builder.buildPopup().showInFocusCenter();
  }

  private static final EditorNotificationPanel.ActionHandler NOOP = new EditorNotificationPanel.ActionHandler() {
    @Override
    public void handlePanelActionClick(@NotNull EditorNotificationPanel panel, @NotNull HyperlinkEvent event) {
    }

    @Override
    public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    }
  };
}
