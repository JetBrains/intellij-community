/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;

/**
 * @author mike
 */
public abstract class FileStatusManager {
  public static FileStatusManager getInstance(Project project) {
    return project.getComponent(FileStatusManager.class);
  }

  public abstract FileStatus getStatus(VirtualFile virtualFile);

  public abstract void fileStatusesChanged();
  public abstract void fileStatusChanged(VirtualFile file);

  public abstract void addFileStatusListener(FileStatusListener listener);
  public abstract void removeFileStatusListener(FileStatusListener listener);

  /**
   * @deprecated Use getStatus(file).getText()} instead
   */
  public String getStatusText(VirtualFile file){
    return getStatus(file).getText();
  }

  /**
   * @deprecated Use getStatus(file).getColor()} instead
   */
  public Color getStatusColor(VirtualFile file){
    return getStatus(file).getColor();
  }  
}
