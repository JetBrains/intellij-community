// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.macos;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.fileTypes.impl.associate.SystemFileTypeAssociator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MacOSFileTypeAssociator implements SystemFileTypeAssociator {
  @Override
  public void associateFileTypes(@NotNull List<? extends FileType> fileTypes) throws OSFileAssociationException {
    LaunchServiceUpdater updater = new LaunchServiceUpdater(getAppBundleIdentifier());
    updater.addFileTypes(fileTypes);
    updater.update();
  }

  @NotNull
  private static String getAppBundleIdentifier() throws OSFileAssociationException {
    AppInfoPListReader infoPListReader = new AppInfoPListReader();
    infoPListReader.loadPList();
    String bundleId = infoPListReader.getBundleIdentifier();
    if (bundleId == null) {
      throw new OSFileAssociationException("Can't find BundleIdentifier from application Info.plist");
    }
    return bundleId;
  }

  @Override
  public boolean isOsRestartRequired() {
    return true;
  }
}
