package com.intellij.platform;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;

/**
 * @author yole
 */
public interface DirectoryProjectGenerator {
  ExtensionPointName<DirectoryProjectGenerator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectGenerator");

  @Nls
  String getName();

  void generateProject(final Project project, final VirtualFile baseDir);
}
