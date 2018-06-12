/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaSdkType extends SdkType implements JavaSdkType {
  public static SimpleJavaSdkType getInstance() {
    return SdkType.findInstance(SimpleJavaSdkType.class);
  }

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
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) { }

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
    return suggestJavaSdkName(this, currentSdkName, sdkHome);
  }

  public static String suggestJavaSdkName(JavaSdkType javaSdkType, String currentSdkName, String sdkHome) {
    assert javaSdkType instanceof SdkType;
    JavaVersion version = JavaVersion.tryParse(((SdkType)javaSdkType).getVersionString(sdkHome));
    if (version == null) return currentSdkName;

    StringBuilder suggested = new StringBuilder();
    if (version.feature < 9) suggested.append("1.");
    suggested.append(version.feature);
    if (version.ea) suggested.append("-ea");
    return suggested.toString();
  }

  @Override
  public final String getVersionString(final String sdkHome) {
    JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(sdkHome);
    return jdkInfo != null ? JdkVersionDetector.formatVersionString(jdkInfo.version) : null;
  }

  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkType() {
    return sdkTypeId -> !(sdkTypeId instanceof SimpleJavaSdkType);
  }

  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkType(@Nullable Condition<SdkTypeId> condition) {
    return sdkTypeId -> notSimpleJavaSdkType().value(sdkTypeId) && (condition == null || condition.value(sdkTypeId));
  }
}