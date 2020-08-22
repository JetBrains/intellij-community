// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.macos;

import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.ide.lightEdit.actions.associate.SystemFileTypeAssociator;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MacOSFileTypeAssociator implements SystemFileTypeAssociator {
  @Override
  public void associateFileTypes(@NotNull List<FileType> fileTypes) throws FileAssociationException {
    LaunchServiceUpdater updater = new LaunchServiceUpdater(getAppBundleIdentifier());
    updater.addFileTypes(fileTypes);
    updater.update();
  }

  @NotNull
  private static String getAppBundleIdentifier() throws FileAssociationException {
    AppInfoPListReader infoPListReader = new AppInfoPListReader();
    infoPListReader.loadPList();
    String bundleId = infoPListReader.getBundleIdentifier();
    if (bundleId == null) {
      throw new FileAssociationException("Can't find BundleIdentifier from application Info.plist");
    }
    return bundleId;
  }

}
