/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class EverythingGlobalScope extends GlobalSearchScope {
  public EverythingGlobalScope(Project project) {
    super(project);
  }

  public EverythingGlobalScope() {
  }

  public int compare(final VirtualFile file1, final VirtualFile file2) {
    return 0;
  }

  public boolean contains(final VirtualFile file) {
    return true;
  }

  public boolean isSearchInLibraries() {
    return true;
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return true;
  }
}