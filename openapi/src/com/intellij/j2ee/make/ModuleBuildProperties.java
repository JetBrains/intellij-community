/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.module.Module;

public abstract class ModuleBuildProperties {
  public static ModuleBuildProperties getInstance(Module module) {
    return module.getComponent(ModuleBuildProperties.class);
  }

  public abstract String getArchiveExtension();

  public abstract String getJarPath();

  public abstract String getExplodedPath();
  
  public abstract Module getModule();

  public abstract boolean isJarEnabled();

  public abstract boolean isExplodedEnabled();

  public abstract boolean isBuildOnFrameDeactivation();

  public abstract boolean isSyncExplodedDir();

  public String getPresentableName() {
    return getModule().getName();
  }
}