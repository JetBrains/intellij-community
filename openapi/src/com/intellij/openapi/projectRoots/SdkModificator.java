/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.vfs.VirtualFile;

public interface SdkModificator {
  String getName();

  void setName(String name);

  String getHomePath();

  void setHomePath(String path);

  String getVersionString();

  void setVersionString(String versionString);

  SdkAdditionalData getSdkAdditionalData();

  void setSdkAdditionalData(SdkAdditionalData data);

  // todo: replace ProjectRootType with OrderRootType?
  VirtualFile[] getRoots(ProjectRootType rootType);

  void addRoot(VirtualFile root, ProjectRootType rootType);

  void removeRoot(VirtualFile root, ProjectRootType rootType);

  void removeRoots(ProjectRootType rootType);

  void removeAllRoots();

  void commitChanges();

  boolean isWritable();
}
