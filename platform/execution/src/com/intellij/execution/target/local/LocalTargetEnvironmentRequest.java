// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.*;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LocalTargetEnvironmentRequest extends BaseTargetEnvironmentRequest {
  private static int nextSyntheticId = 0;
  private Volume myDefaultVolume;
  private final Map<String, LocalUploadVolume> myUploadRoots = new LinkedHashMap<>();

  public LocalTargetEnvironmentRequest() {
    super();
  }

  private LocalTargetEnvironmentRequest(@NotNull Set<TargetEnvironment.UploadRoot> uploadVolumes,
                                        @NotNull Set<TargetEnvironment.DownloadRoot> downloadVolumes,
                                        @NotNull Set<TargetEnvironment.TargetPortBinding> targetPortBindings,
                                        @NotNull Set<TargetEnvironment.LocalPortBinding> localPortBindings) {
    super(uploadVolumes, downloadVolumes, targetPortBindings, localPortBindings);
  }

  @Override
  public @NotNull TargetEnvironmentRequest duplicate() {
    return new LocalTargetEnvironmentRequest(
      new HashSet<>(getUploadVolumes()),
      new HashSet<>(getDownloadVolumes()),
      new HashSet<>(getTargetPortBindings()),
      new HashSet<>(getLocalPortBindings()));
  }

  @NotNull
  private GeneralCommandLine.ParentEnvironmentType myParentEnvironmentType = GeneralCommandLine.ParentEnvironmentType.CONSOLE;

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @Nullable
  @Override
  public TargetEnvironmentConfiguration getConfiguration() {
    return null;
  }

  @Override
  @NotNull
  public Volume getDefaultVolume() {
    if (myDefaultVolume == null) {
      myDefaultVolume = createTempVolume();
    }
    return myDefaultVolume;
  }

  @NotNull
  private Volume createTempVolume() {
    String id = nextSyntheticId();
    return myUploadRoots.computeIfAbsent(id, path -> new LocalUploadVolume(this, id));
  }

  @NotNull
  @Override
  public LocalTargetEnvironment prepareEnvironment(@NotNull TargetProgressIndicator progressIndicator) throws ExecutionException {
    LocalTargetEnvironment environment = new LocalTargetEnvironment(this);
    environmentPrepared(environment, progressIndicator);
    return environment;
  }

  @NotNull
  GeneralCommandLine.ParentEnvironmentType getParentEnvironmentType() {
    return myParentEnvironmentType;
  }

  private static String nextSyntheticId() {
    return LocalTargetEnvironmentRequest.class.getSimpleName() + ":volume:" + (nextSyntheticId++); //NON-NLS
  }

  public void setParentEnvironmentType(@NotNull GeneralCommandLine.ParentEnvironmentType parentEnvironmentType) {
    myParentEnvironmentType = parentEnvironmentType;
  }

  private static class LocalUploadVolume implements TargetEnvironmentRequest.Volume {
    private final LocalTargetEnvironmentRequest myRequest;
    private final String myVolumeId;

    LocalUploadVolume(@NotNull LocalTargetEnvironmentRequest request, @NotNull String volumeId) {
      myRequest = request;
      myVolumeId = volumeId;
    }

    @NotNull
    @Override
    public String getVolumeId() {
      return myVolumeId;
    }

    @Override
    public @NotNull Platform getPlatform() {
      return myRequest.getTargetPlatform().getPlatform();
    }

    @NotNull
    @Override
    public TargetValue<String> createUpload(@NotNull String localPath) {
      return TargetValue.fixed(localPath);
    }
  }
}
