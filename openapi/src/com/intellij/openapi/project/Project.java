/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;



/**
 * Project interface class.
 */
public interface Project extends ComponentManager {
  VirtualFile getProjectFile();
  String getName();
  /**
   * @return Returns an "java.io.File" path.
   */
  String getProjectFilePath();

  VirtualFile getWorkspaceFile();

  void save();

  boolean isDisposed();

  boolean isOpen();

  boolean isInitialized();

  boolean isDefault();

  PomModel getModel();
}
