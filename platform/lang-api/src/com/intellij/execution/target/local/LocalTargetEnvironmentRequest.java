// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.HostPort;
import com.intellij.execution.target.BaseTargetEnvironmentRequest;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetPlatform;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LocalTargetEnvironmentRequest extends BaseTargetEnvironmentRequest {
  private static int nextSyntheticId = 0;
  private Volume myDefaultVolume;
  private final Map<String, LocalDownloadVolume> myDownloadRoots = new LinkedHashMap<>();
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

  @Override
  @NotNull
  public Volume getDefaultVolume() {
    if (myDefaultVolume == null) {
      myDefaultVolume = createUploadRoot(null, true);
    }
    return myDefaultVolume;
  }

  @Override
  @NotNull
  public Volume createUploadRoot(@Nullable String remoteRootPath, boolean temporary) {
    String id = nextSyntheticId();
    if (remoteRootPath == null) {
      remoteRootPath = id;
    }
    return myUploadRoots.computeIfAbsent(remoteRootPath, path -> new LocalUploadVolume(this, id));
  }

  @Override
  @NotNull
  public DownloadableVolume createDownloadRoot(@Nullable String remoteRootPath) {
    String id = nextSyntheticId();
    if (remoteRootPath == null) {
      remoteRootPath = "";
    }
    return myDownloadRoots.computeIfAbsent(remoteRootPath, path -> new LocalDownloadVolume(this, id, path));
  }

  @NotNull
  @Override
  public TargetValue<Integer> bindTargetPort(int targetPort) {
    return TargetValue.fixed(targetPort);
  }

  @Override
  public @NotNull TargetValue<HostPort> bindLocalPort(int localPort) {
    return TargetValue.fixed(new HostPort("localhost", localPort));
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

  private static class LocalDownloadVolume extends LocalUploadVolume implements TargetEnvironmentRequest.DownloadableVolume {
    private final String myRootPath;

    LocalDownloadVolume(@NotNull LocalTargetEnvironmentRequest request, @NotNull String volumeId, @NotNull String rootPath) {
      super(request, volumeId);
      myRootPath = rootPath;
    }

    @Override
    @NotNull
    public String getRemoteRoot() {
      return myRootPath;
    }

    @Override
    @NotNull
    public TargetValue<String> createDownload(@NotNull String rootRelativePath) {
      String fullPath = concatPaths(myRootPath, rootRelativePath);
      return TargetValue.fixed(fullPath);
    }

    @NotNull
    private static String concatPaths(@NotNull String parent, @NotNull String child) {
      return StringUtil.isEmptyOrSpaces(parent) ? child : parent + Platform.current().fileSeparator + child;
    }
  }
}
