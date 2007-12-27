/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.Nullable;

public abstract class CompilerProjectExtension extends RootsContainerProjectExtesion {

  public static CompilerProjectExtension getInstance(Project project) {
    for (ProjectExtension extension : Extensions.getExtensions(EP_NAME, project)) {
      if (CompilerProjectExtension.class.isAssignableFrom(extension.getClass())) return (CompilerProjectExtension)extension;
    }
    return null;
  }

  @Nullable
  public abstract VirtualFile getCompilerOutput();

  @Nullable
  public abstract String getCompilerOutputUrl();

  public abstract VirtualFilePointer getCompilerOutputPointer();

  public abstract void setCompilerOutputPointer(VirtualFilePointer pointer);

  public abstract void setCompilerOutputUrl(String compilerOutputUrl);
}