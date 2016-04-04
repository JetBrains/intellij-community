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

package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaSdkType extends SdkType implements JavaSdkType {
  // do not use javaw.exe for Windows because of issues with encoding
  @NonNls private static final String VM_EXE_NAME = "java";

  public SimpleJavaSdkType() {
    super("SimpleJavaSdkType");
  }

  public Sdk createJdk(final String jdkName, final String home) {
    final Sdk jdk = ProjectJdkTable.getInstance().createSdk(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.commitChanges();
    return jdk;
  }

  @NotNull
  @Override
  public String getPresentableName() {
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
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  @NonNls
  public String getToolsPath(@NotNull Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  @Override
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    return path;
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return JdkUtil.checkForJdk(new File(path));
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
