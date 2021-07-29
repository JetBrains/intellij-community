// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

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

  @Nullable
  @Override
  public String suggestHomePath() {
    return JdkFinder.getInstance().defaultJavaLocation();
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths() {
    //there is no need to search for JDKs if there is JavaSdkImpl registered
    if (!notSimpleJavaSdkTypeIfAlternativeExists().value(this)) {
      return Collections.emptyList();
    }

    return JdkFinder.getInstance().suggestHomePaths();
  }

  @Override
  public boolean isValidSdkHome(@NotNull String path) {
    return JdkUtil.checkForJdk(path);
  }

  @NotNull
  @Override
  public String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    String suggestedName = JdkUtil.suggestJdkName(getVersionString(sdkHome));
    return suggestedName != null ? suggestedName : currentSdkName != null ? currentSdkName : "";
  }

  @Override
  public final String getVersionString(final String sdkHome) {
    JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(sdkHome);
    return jdkInfo != null ? JdkVersionDetector.formatVersionString(jdkInfo.version) : null;
  }

  @NotNull
  @Override
  public Comparator<String> versionStringComparator() {
    return (sdk1, sdk2) -> {
      return Comparing.compare(JavaVersion.tryParse(sdk1), JavaVersion.tryParse(sdk2));
    };
  }

  private static final Condition<SdkTypeId> TRUE = sdkTypeId -> true;
  private static final Condition<SdkTypeId> NOT_SIMPLE_JAVA_TYPE = sdkTypeId -> !(sdkTypeId instanceof SimpleJavaSdkType);
  private static final Condition<SdkTypeId> NOT_DEPENDENT_TYPE = sdkTypeId -> (sdkTypeId instanceof SdkType && ((SdkType)sdkTypeId).getDependencyType() == null);

  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkType() {
    return NOT_SIMPLE_JAVA_TYPE;
  }

  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkType(@Nullable Condition<? super SdkTypeId> condition) {
    if (condition == null) return NOT_SIMPLE_JAVA_TYPE;
    return sdkTypeId -> NOT_SIMPLE_JAVA_TYPE.value(sdkTypeId) && condition.value(sdkTypeId);
  }

  /**
   * @return an SdkTypeId predicate that returns true only for JavaSdkType instances.
   * If there are more more JavaSdkType non-dependent SDK Types, that predicate also
   * filters out the SimpleJavaSdkType implementation
   */
  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkTypeIfAlternativeExists() {
    boolean hasNotSimple = Stream.of(SdkType.getAllTypes())
      .filter(notSimpleJavaSdkType()::value)
      .anyMatch(it -> it instanceof JavaSdkType && it.getDependencyType() == null && !((JavaSdkType)it).isDependent());

    if (hasNotSimple) {
      //we found another JavaSdkType (e.g. JavaSdkImpl), there is no need for SimpleJavaSdkType
      return NOT_SIMPLE_JAVA_TYPE;
    } else {
      //there is only one JavaSdkType, so it is no need to filter anything
      return TRUE;
    }
  }

  /**
   * @return an SdkTypeId predicate that returns true only for JavaSdkType which is not
   * a dependent SDK type. Moreover, if there are several matches, the SimpleJavaSdkType
   * is filtered out too
   */
  @NotNull
  public static Condition<SdkTypeId> notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType() {
    Condition<SdkTypeId> preferablyNotSimple = notSimpleJavaSdkTypeIfAlternativeExists();
    return sdkType -> sdkType instanceof JavaSdkType && NOT_DEPENDENT_TYPE.value(sdkType) && preferablyNotSimple.value(sdkType);
  }
}
