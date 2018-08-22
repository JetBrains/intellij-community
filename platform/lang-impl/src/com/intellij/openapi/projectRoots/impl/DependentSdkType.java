// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public abstract class DependentSdkType extends SdkType {

  public DependentSdkType(@NonNls String name) {
    super(name);
  }

  /**
   * Checks if dependencies satisfied.
   */
  protected boolean checkDependency(SdkModel sdkModel) {
    return ContainerUtil.find(sdkModel.getSdks(), sdk -> isValidDependency(sdk)) != null;
  }

  protected abstract boolean isValidDependency(Sdk sdk);

  public abstract String getUnsatisfiedDependencyMessage();

  @Override
  public boolean supportsCustomCreateUI() {
    return true;
  }

  @Override
  public void showCustomCreateUI(@NotNull final SdkModel sdkModel, @NotNull JComponent parentComponent, @NotNull final Consumer<Sdk> sdkCreatedCallback) {
    if (!checkDependency(sdkModel)) {
      if (Messages.showOkCancelDialog(parentComponent, getUnsatisfiedDependencyMessage(), "Cannot Create SDK", Messages.getWarningIcon()) != Messages.OK) {
        return;
      }
      if (fixDependency(sdkModel, sdkCreatedCallback) == null) {
        return;
      }
    }

    createSdkOfType(sdkModel, this, sdkCreatedCallback);
  }

  @Override
  public abstract SdkType getDependencyType();

  protected Sdk fixDependency(SdkModel sdkModel, Consumer<Sdk> sdkCreatedCallback) {
    return createSdkOfType(sdkModel, getDependencyType(), sdkCreatedCallback);
  }

  protected static Sdk createSdkOfType(final SdkModel sdkModel,
                                  final SdkType sdkType,
                                  final Consumer<Sdk> sdkCreatedCallback) {
    final Ref<Sdk> result = new Ref<>(null);
    SdkConfigurationUtil.selectSdkHome(sdkType, home -> {
      String newSdkName = SdkConfigurationUtil.createUniqueSdkName(sdkType, home, Arrays.asList(sdkModel.getSdks()));
      final ProjectJdkImpl newJdk = new ProjectJdkImpl(newSdkName, sdkType);
      newJdk.setHomePath(home);

      sdkCreatedCallback.consume(newJdk);
      result.set(newJdk);
    });
    return result.get();
  }
}
