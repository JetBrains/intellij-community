// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetEnvironmentVolume;
import com.intellij.execution.target.TargetPlatform;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LocalTargetEnvironmentRequest implements TargetEnvironmentRequest {
  private final Map<TargetEnvironmentVolume.VolumeMode, TargetEnvironmentVolume> myVolumes = new LinkedHashMap<>();

  @NotNull
  private GeneralCommandLine.ParentEnvironmentType myParentEnvironmentType = GeneralCommandLine.ParentEnvironmentType.CONSOLE;

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @Override
  @NotNull
  public TargetEnvironmentVolume requestVolume(TargetEnvironmentVolume.@NotNull VolumeMode mode) {
    return myVolumes.computeIfAbsent(mode, aMode -> new LocalVolume(this, aMode));
  }

  @Override
  public Iterable<TargetEnvironmentVolume> getVolumes() {
    return myVolumes.values();
  }

  @NotNull
  @Override
  public TargetValue<Integer> bindTargetPort(int targetPort) {
    return TargetValue.fixed(targetPort);
  }

  @NotNull
  GeneralCommandLine.ParentEnvironmentType getParentEnvironmentType() {
    return myParentEnvironmentType;
  }

  public void setParentEnvironmentType(@NotNull GeneralCommandLine.ParentEnvironmentType parentEnvironmentType) {
    myParentEnvironmentType = parentEnvironmentType;
  }

  private static class LocalVolume implements TargetEnvironmentVolume {
    private final LocalTargetEnvironmentRequest myRequest;
    private final VolumeMode myMode;
    private final String myRootPath;

    @Nullable
    static String getRootPath(VolumeMode mode) {
      if (mode instanceof VolumeMode.Download) {
        return ((VolumeMode.Download)mode).getRemoteRoot();
      }
      if (mode instanceof VolumeMode.Upload) {
        return ((VolumeMode.Upload)mode).getRemoteRoot();
      }
      throw new IllegalArgumentException("Unknown mode: " + mode);
    }

    LocalVolume(LocalTargetEnvironmentRequest request, VolumeMode mode) {
      myRequest = request;
      myMode = mode;
      myRootPath = getRootPath(mode);
      if (myRootPath == null) {
        //FIXME: reconsider, need persisted volumes use case
        throw new IllegalArgumentException("Incompatible mode, can't find root path: " + mode);
      }
    }

    @NotNull
    @Override
    public LocalTargetEnvironmentRequest getRequest() {
      return myRequest;
    }

    @NotNull
    @Override
    public TargetEnvironmentVolume.VolumeMode getMode() {
      return myMode;
    }

    @NotNull
    @Override
    public TargetValue<String> createUpload(@NotNull String localPath) {
      return TargetValue.fixed(localPath);
    }

    @NotNull
    @Override
    public TargetValue<String> createDownload(@NotNull String rootRelativePath) {
      String fullPath = concatPaths(myRootPath, rootRelativePath);
      return TargetValue.fixed(fullPath);
    }

    // FIXME check separator
    private static String concatPaths(String parent, String child) {
      return parent + "/" + child;
    }
  }
}
