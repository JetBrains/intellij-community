// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used as a plug for all SDKs which type cannot be determined (for example, plugin that registered a custom type has been deinstalled)
 * @author Eugene Zhuravlev
 */
public class UnknownSdkType extends SdkType{
  private static final Map<String, UnknownSdkType> ourTypeNameToInstanceMap = new ConcurrentHashMap<>();

  /**
   * @param typeName the name of the SDK type that this SDK serves as a plug for
   */
  private UnknownSdkType(@NotNull String typeName) {
    super(typeName);
  }

  @NotNull
  public static UnknownSdkType getInstance(@NotNull String typeName) {
    return ourTypeNameToInstanceMap.computeIfAbsent(typeName, UnknownSdkType::new);
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return false;
  }

  @Override
  public String getVersionString(String sdkHome) {
    return "";
  }

  @NotNull
  @Override
  public String suggestSdkName(@Nullable String currentSdkName, String sdkHome) {
    return currentSdkName != null ? currentSdkName : "";
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  public String getBinPath(Sdk sdk) {
    return null;
  }

  public String getToolsPath(Sdk sdk) {
    return null;
  }

  public String getVMExecutablePath(Sdk sdk) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return ProjectBundle.message("sdk.unknown.name");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.UnknownJdk;
  }
}
