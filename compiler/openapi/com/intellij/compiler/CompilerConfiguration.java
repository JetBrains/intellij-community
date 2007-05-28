package com.intellij.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class CompilerConfiguration {
  public static CompilerConfiguration getInstance(Project project) {
    return project.getComponent(CompilerConfiguration.class);
  }

  public abstract boolean isExcludedFromCompilation(VirtualFile virtualFile);

  public abstract boolean isResourceFile(String name);
}