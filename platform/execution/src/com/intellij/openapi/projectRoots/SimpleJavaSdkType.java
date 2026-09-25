// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;

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
    String homePath = FileUtil.toSystemIndependentName(home);
    sdkModificator.setHomePath(homePath);
    sdkModificator.setVersionString(this.getVersionString(homePath));

    // This SDK not in the storage, so it's OK to apply changes from `SdkModificator` to the storage, related to SDK only, not global WSM
    sdkModificator.applyChangesWithoutWriteAction();
    return jdk;
  }

  @Override
  public @NotNull String getPresentableName() {
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
  public @Nullable String suggestHomePath() {
    return JdkFinder.getInstance().defaultJavaLocation();
  }

  @Override
  public @NotNull @Unmodifiable Collection<String> suggestHomePaths() {
    return suggestHomePaths(null);
  }

  @Override
  public @NotNull @Unmodifiable Collection<String> suggestHomePaths(@Nullable Project project) {
    //there is no need to search for JDKs if there is JavaSdkImpl registered
    if (!notSimpleJavaSdkTypeIfAlternativeExists().test(this)) {
      return Collections.emptyList();
    }

    return JdkFinder.getInstance().suggestHomePaths(project);
  }

  @Override
  public boolean isValidSdkHome(@NotNull String path) {
    return JdkUtil.checkForJdk(path);
  }

  @Override
  public @NotNull String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    String suggestedName = JdkUtil.suggestJdkName(getVersionString(sdkHome));
    return suggestedName != null ? suggestedName : currentSdkName != null ? currentSdkName : "";
  }

  @Override
  public final String getVersionString(final @NotNull String sdkHome) {
    JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(sdkHome);
    return jdkInfo != null ? JdkVersionDetector.formatVersionString(jdkInfo.version) : null;
  }

  @Override
  public @NotNull Comparator<String> versionStringComparator() {
    return (sdk1, sdk2) -> {
      return Comparing.compare(JavaVersion.tryParse(sdk1), JavaVersion.tryParse(sdk2));
    };
  }

  private static final Predicate<SdkTypeId> TRUE = sdkTypeId -> true;
  private static final Predicate<SdkTypeId> NOT_SIMPLE_JAVA_TYPE = sdkTypeId -> !(sdkTypeId instanceof SimpleJavaSdkType);
  private static final Predicate<SdkTypeId> NOT_DEPENDENT_TYPE = sdkTypeId -> (sdkTypeId instanceof SdkType && ((SdkType)sdkTypeId).getDependencyType() == null);

  public static @NotNull Predicate<SdkTypeId> notSimpleJavaSdkType() {
    return NOT_SIMPLE_JAVA_TYPE;
  }

  public static @NotNull Predicate<SdkTypeId> notSimpleJavaSdkType(@Nullable Predicate<? super SdkTypeId> condition) {
    if (condition == null) {
      return NOT_SIMPLE_JAVA_TYPE;
    }
    return sdkTypeId -> NOT_SIMPLE_JAVA_TYPE.test(sdkTypeId) && condition.test(sdkTypeId);
  }

  /**
   * @return an SdkTypeId predicate that returns true only for JavaSdkType instances.
   * If there are more more JavaSdkType non-dependent SDK Types, that predicate also
   * filters out the SimpleJavaSdkType implementation
   */
  public static @NotNull Predicate<SdkTypeId> notSimpleJavaSdkTypeIfAlternativeExists() {
    boolean hasNotSimple = false;
    Predicate<SdkTypeId> condition = notSimpleJavaSdkType();
    for (SdkType it : SdkType.getAllTypeList()) {
      if (condition.test(it)) {
        if (it instanceof JavaSdkType && it.getDependencyType() == null && !((JavaSdkType)it).isDependent()) {
          hasNotSimple = true;
          break;
        }
      }
    }

    if (hasNotSimple) {
      //we found another JavaSdkType (e.g. JavaSdkImpl), there is no need for SimpleJavaSdkType
      return NOT_SIMPLE_JAVA_TYPE;
    }
    else {
      //there is only one JavaSdkType, so it is no need to filter anything
      return TRUE;
    }
  }

  /**
   * @return an SdkTypeId predicate that returns true only for JavaSdkType which is not
   * a dependent SDK type. Moreover, if there are several matches, the SimpleJavaSdkType
   * is filtered out too
   */
  public static @NotNull Condition<SdkTypeId> notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType() {
    Predicate<SdkTypeId> preferablyNotSimple = notSimpleJavaSdkTypeIfAlternativeExists();
    return sdkType -> sdkType instanceof JavaSdkType && NOT_DEPENDENT_TYPE.test(sdkType) && preferablyNotSimple.test(sdkType);
  }
}
