package com.intellij.platform;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface DirectoryProjectConfigurator {
  ExtensionPointName<DirectoryProjectConfigurator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectConfigurator");

  void configureProject(Project project, @NotNull VirtualFile baseDir);
}
