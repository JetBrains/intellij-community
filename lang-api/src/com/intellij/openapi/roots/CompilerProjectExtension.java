/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.Nullable;

public abstract class CompilerProjectExtension {

  public static CompilerProjectExtension getInstance(Project project) {
    return ServiceManager.getService(project, CompilerProjectExtension.class);
  }

  @Nullable
  public abstract VirtualFile getCompilerOutput();

  @Nullable
  public abstract String getCompilerOutputUrl();

  public abstract VirtualFilePointer getCompilerOutputPointer();

  public abstract void setCompilerOutputPointer(VirtualFilePointer pointer);

  public abstract void setCompilerOutputUrl(String compilerOutputUrl);
}