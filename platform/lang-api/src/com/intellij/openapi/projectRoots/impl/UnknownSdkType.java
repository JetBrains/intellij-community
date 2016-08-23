/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Used as a plug for all SDKs which type cannot be determined (for example, plugin that registered a custom type has been deinstalled)
 * @author Eugene Zhuravlev
 *         Date: Dec 11, 2004
 */
public class UnknownSdkType extends SdkType{
  private static final Map<String, UnknownSdkType> ourTypeNameToInstanceMap = new HashMap<>();

  /**
   * @param typeName the name of the SDK type that this SDK serves as a plug for
   */
  private UnknownSdkType(@NotNull String typeName) {
    super(typeName);
  }

  @NotNull
  public static UnknownSdkType getInstance(@NotNull String typeName) {
    UnknownSdkType instance = ourTypeNameToInstanceMap.get(typeName);
    if (instance == null) {
      instance = new UnknownSdkType(typeName);
      ourTypeNameToInstanceMap.put(typeName, instance);
    }
    return instance;
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

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return currentSdkName;
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
