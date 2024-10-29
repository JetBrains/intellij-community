// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public abstract class DependentSdkType extends SdkType {

  public DependentSdkType(@NonNls @NotNull String name) {
    super(name);
  }

  /**
   * Checks if dependencies satisfied.
   */
  protected boolean checkDependency(@NotNull SdkModel sdkModel) {
    return ContainerUtil.find(sdkModel.getSdks(), sdk -> isValidDependency(sdk)) != null;
  }

  protected abstract boolean isValidDependency(@NotNull Sdk sdk);

  public abstract @NotNull @NlsContexts.DialogMessage String getUnsatisfiedDependencyMessage();

  @Override
  public boolean supportsCustomCreateUI() {
    return true;
  }

  @Override
  public void showCustomCreateUI(@NotNull SdkModel sdkModel, @NotNull JComponent parentComponent, @Nullable Sdk selectedSdk,
                                 @NotNull Consumer<? super Sdk> sdkCreatedCallback) {
    if (!checkDependency(sdkModel)) {
      if (Messages.showOkCancelDialog(parentComponent, getUnsatisfiedDependencyMessage(),
                                      ProjectBundle.message("dialog.title.cannot.create.sdk"), Messages.getWarningIcon()) != Messages.OK) {
        return;
      }
      if (fixDependency(sdkModel, sdkCreatedCallback) == null) {
        return;
      }
    }

    createSdkOfType(sdkModel, this, sdkCreatedCallback);
  }

  @Override
  public abstract @NotNull SdkType getDependencyType();

  protected Sdk fixDependency(@NotNull SdkModel sdkModel, @NotNull Consumer<? super Sdk> sdkCreatedCallback) {
    return createSdkOfType(sdkModel, getDependencyType(), sdkCreatedCallback);
  }

  protected static Sdk createSdkOfType(@NotNull SdkModel sdkModel,
                                       @NotNull SdkType sdkType,
                                       @NotNull Consumer<? super Sdk> sdkCreatedCallback) {
    final Ref<Sdk> result = new Ref<>(null);
    SdkConfigurationUtil.selectSdkHome(sdkType, home -> {
      final Sdk newSdk = SdkConfigurationUtil.createSdk(Arrays.asList(sdkModel.getSdks()), home, sdkType, null, null);

      sdkCreatedCallback.consume(newSdk);
      result.set(newSdk);
    });
    return result.get();
  }
}
