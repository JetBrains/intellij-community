// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.StubUpdatingIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PushedFilePropertiesRetrieverImpl implements PushedFilePropertiesRetriever {
  @Override
  public @NotNull List<String> dumpSortedPushedProperties(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      throw new IllegalArgumentException("file " + file + " is expected to be a regular file");
    }
    if (!StubUpdatingIndex.USE_SNAPSHOT_MAPPINGS) {
      return Collections.emptyList();
    }

    List<String> properties = null;
    for (FilePropertyPusher<?> extension : FilePropertyPusher.EP_NAME.getExtensionList()) {
      Object property;
      VirtualFile vfsObject;
      if (extension.pushDirectoriesOnly()) {
        vfsObject = file.getParent();
      }
      else {
        vfsObject = file;
      }
      property = extension.getFilePropertyKey().getPersistentValue(vfsObject);
      if (property != null) {
        if (properties == null) {
          properties = new ArrayList<>();
        }
        properties.add(property.toString());
      }
    }

    if (properties == null) {
      return Collections.emptyList();
    }

    properties.sort(null);
    return properties;
  }
}
