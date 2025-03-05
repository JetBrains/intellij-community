// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class DefaultExternalSystemJdkProvider implements ExternalSystemJdkProvider {
  @Override
  public @NotNull SdkType getJavaSdkType() {
    return SimpleJavaSdkType.getInstance();
  }

  @Override
  public @NotNull Sdk getInternalJdk() {
    final String jdkHome = SystemProperties.getJavaHome();
    SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
    return simpleJavaSdkType.createJdk(simpleJavaSdkType.suggestSdkName(null, jdkHome), jdkHome);
  }

  @Override
  public @NotNull Sdk createJdk(@Nullable String jdkName, @NotNull String homePath) {
    SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
    String sdkName = jdkName != null ? jdkName : simpleJavaSdkType.suggestSdkName(null, homePath);
    return simpleJavaSdkType.createJdk(sdkName, homePath);
  }
}
