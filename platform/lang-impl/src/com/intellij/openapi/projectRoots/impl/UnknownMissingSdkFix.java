// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

final class UnknownMissingSdkFix implements UnknownSdkFix {
  private static final Logger LOG = Logger.getInstance(UnknownMissingSdkFix.class);

  private final @Nullable Project myProject;
  private final @NotNull UnknownSdk mySdk;
  private final @Nullable UnknownSdkFixAction myAction;

  UnknownMissingSdkFix(@Nullable Project project,
                       @NotNull UnknownSdk unknownSdk,
                       @Nullable UnknownSdkFixAction action) {
    myProject = project;
    mySdk = unknownSdk;
    myAction = action;
  }

  @NotNull
  String getSdkNameForUi() {
    return UnknownMissingSdk.getSdkNameForUi(mySdk);
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project) {
    return myProject == null || project == myProject;
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project, @NotNull VirtualFile file) {
    return isRelevantFor(project) && mySdk.getSdkType().isRelevantForFile(project, file);
  }

  private @NotNull SdkType getSdkType() {
    return mySdk.getSdkType();
  }

  @Override
  public @Nullable UnknownSdkFixAction getSuggestedFixAction() {
    return myAction;
  }

  @Override
  public @Nls @NotNull String getIntentionActionText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @NotNull EditorNotificationPanel.ActionHandler getConfigureActionHandler(@NotNull Project project) {
    return SdkPopupFactory
      .newBuilder()
      .withProject(myProject)
      .withSdkTypeFilter(type -> Objects.equals(type, mySdk.getSdkType()))
      .onSdkSelected(sdk -> {
        registerNewSdkInJdkTable(mySdk.getSdkName(), sdk);
      })
      .buildEditorNotificationPanelHandler();
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("notification.text.config.unknown.sdk", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @Nls @NotNull String getConfigureActionText() {
    return ProjectBundle.message("action.text.config.unknown.sdk.configure");
  }

  @Override
  public String toString() {
    return "SdkFixInfo {" + mySdk + ", " + myAction + "}";
  }

  static @NotNull Sdk createNewSdk(@NotNull UnknownSdk unknownSdk,
                                   @NotNull Supplier<@NotNull String> suggestedSdkName) {
    var actualSdkName = unknownSdk.getSdkName();
    if (actualSdkName == null) {
      actualSdkName = suggestedSdkName.get();
    }

    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    actualSdkName = SdkConfigurationUtil.createUniqueSdkName(actualSdkName, List.of(jdkTable.getAllJdks()));
    return jdkTable.createSdk(actualSdkName, unknownSdk.getSdkType());
  }

  static void registerNewSdkInJdkTable(@Nullable String sdkName, @NotNull Sdk sdk) {
    WriteAction.run(() -> {
      ProjectJdkTable table = ProjectJdkTable.getInstance();
      if (sdkName != null) {
        Sdk clash = table.findJdk(sdkName);
        if (clash != null) {
          LOG.warn("SDK with name " + sdkName + " already exists: clash=" + clash + ", new=" + sdk);
          return;
        }
        SdkModificator mod = sdk.getSdkModificator();
        mod.setName(sdkName);
        mod.commitChanges();
      }

      table.addJdk(sdk);
    });
  }
}
