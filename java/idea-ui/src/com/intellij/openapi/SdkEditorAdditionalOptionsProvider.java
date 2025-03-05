// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class SdkEditorAdditionalOptionsProvider {
  private static final ExtensionPointName<SdkEditorAdditionalOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.sdkEditorAdditionalOptionsProvider");

  private final SdkType myType;

  protected SdkEditorAdditionalOptionsProvider(SdkType type) {
    myType = type;
  }

  public static @Unmodifiable List<SdkEditorAdditionalOptionsProvider> getSdkOptionsFactory(SdkTypeId sdkType) {
    return ContainerUtil.filter(EP_NAME.getExtensions(), (e) -> sdkType == e.myType);
  }

  public abstract @Nullable AdditionalDataConfigurable createOptions(@NotNull Project project, @NotNull Sdk sdk);
}
