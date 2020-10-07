// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "OSFileAssociationPreferences", storages =  @Storage("osFileIdePreferences.xml"))
public class OSFileAssociationPreferences implements PersistentStateComponent<OSFileAssociationPreferences> {

  public List<String> fileTypeNames = new ArrayList<>();

  public static OSFileAssociationPreferences getInstance() {
    return ServiceManager.getService(OSFileAssociationPreferences.class);
  }

  @Override
  public @Nullable OSFileAssociationPreferences getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull OSFileAssociationPreferences state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void updateFileTypes(List<FileType> fileTypes) {
    fileTypeNames.clear();
    fileTypeNames.addAll(ContainerUtil.map(fileTypes, fileType -> fileType.getName()));
  }

  public boolean contains(@NotNull FileType fileType) {
    return fileTypeNames.stream().anyMatch(name->name.equals(fileType.getName()));
  }
}
