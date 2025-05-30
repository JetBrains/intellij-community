// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DigestUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@State(name = "OSFileAssociationPreferences",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "osFileIdePreferences.xml", roamingType = RoamingType.DISABLED))
public final class OSFileAssociationPreferences implements PersistentStateComponent<OSFileAssociationPreferences> {
  public List<String> fileTypeNames = new ArrayList<>();
  public String ideLocationHash;

  public static OSFileAssociationPreferences getInstance() {
    return ApplicationManager.getApplication().getService(OSFileAssociationPreferences.class);
  }

  @Override
  public @NotNull OSFileAssociationPreferences getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull OSFileAssociationPreferences state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void updateFileTypes(List<? extends FileType> fileTypes) {
    fileTypeNames.clear();
    fileTypeNames.addAll(ContainerUtil.map(fileTypes, fileType -> fileType.getName()));
    updateIdeLocationHash();
  }

  boolean ideLocationChanged() {
    return !Objects.equals(ideLocationHash, getIdeLocationHash());
  }

  void updateIdeLocationHash() {
    ideLocationHash = getIdeLocationHash();
  }

  private static @NotNull String getIdeLocationHash() {
    MessageDigest messageDigest = DigestUtil.md5();
    messageDigest.update(PathManager.getHomePath().getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (byte b : messageDigest.digest()) {
      result.append(String.format("%02X", b));
    }
    return result.toString();
  }

  public boolean contains(@NotNull FileType fileType) {
    return fileTypeNames.contains(fileType.getName());
  }
}
