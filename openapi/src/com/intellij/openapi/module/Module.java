/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModule;

public interface Module extends ComponentManager {
  Module[] EMPTY_ARRAY = new Module[0];

  VirtualFile getModuleFile();

  String getModuleFilePath();

  ModuleType getModuleType();

  Project getProject();

  String getName();

  boolean isDisposed();

  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  void setOption(String optionName, String optionValue);

  String getOptionValue(String optionName);

  PomModule getPom();
}
