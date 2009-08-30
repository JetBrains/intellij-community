/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.TestOnly;

public abstract class JavaPsiFacadeEx extends JavaPsiFacade {
  public static JavaPsiFacadeEx getInstanceEx(Project project) {
    return (JavaPsiFacadeEx)getInstance(project);
  }

  @TestOnly
  public abstract void setAssertOnFileLoadingFilter(final VirtualFileFilter filter);
}