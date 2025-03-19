// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

@ApiStatus.Internal
public interface RemoteSdkFactory<T extends RemoteSdkAdditionalData> {
  Sdk createRemoteSdk(@Nullable Project project, @NotNull T data, @Nullable String sdkName, Collection<Sdk> existingSdks)
    throws RemoteSdkException;

  String generateSdkHomePath(@NotNull T data);

  Sdk createUnfinished(T data, Collection<Sdk> existingSdks);

  String getDefaultUnfinishedName();

  @NotNull
  String sdkName();

  boolean canSaveUnfinished();

  void initSdk(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent) throws RemoteSdkException;
}
