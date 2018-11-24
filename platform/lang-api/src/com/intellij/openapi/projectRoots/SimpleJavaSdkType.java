/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaSdkType extends SdkType implements JavaSdkType {
  public SimpleJavaSdkType() {
    super("SimpleJavaSdkType");
  }

  public Sdk createJdk(@NotNull String jdkName, @NotNull String home) {
    Sdk jdk = ProjectJdkTable.getInstance().createSdk(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.setHomePath(FileUtil.toSystemIndependentName(home));
    sdkModificator.commitChanges();
    return jdk;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    //noinspection UnresolvedPropertyKey
    return ProjectBundle.message("sdk.java.name");
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
  }

  @Override
  public String getBinPath(@NotNull Sdk sdk) {
    return new File(sdk.getHomePath(), "bin").getPath();
  }

  @Override
  public String getToolsPath(@NotNull Sdk sdk) {
    return new File(sdk.getHomePath(), "lib/tools.jar").getPath();
  }

  @Override
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    return new File(sdk.getHomePath(), "bin/java").getPath();
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return JdkUtil.checkForJdk(path);
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return currentSdkName;
  }

  @Override
  public final String getVersionString(final String sdkHome) {
    return SdkVersionUtil.detectJdkVersion(sdkHome);
  }
}