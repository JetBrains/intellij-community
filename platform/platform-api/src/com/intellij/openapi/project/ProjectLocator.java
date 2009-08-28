/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectLocator {
  public static ProjectLocator getInstance() {
    return ServiceManager.getService(ProjectLocator.class);
  }

  @Nullable
  public abstract Project guessProjectForFile(VirtualFile file);
}