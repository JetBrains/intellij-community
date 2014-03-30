package com.intellij.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class NonClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {

  @Override
  public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, Project project) {
    String fileExtension = file.getExtension();
    if ("class".equals(fileExtension) || JavaFileType.DEFAULT_EXTENSION.equals(fileExtension)) {
      for (PsiElementFinder finder : Extensions.getExtensions(PsiElementFinder.EP_NAME, project)) {
        if (finder instanceof NonClasspathClassFinder) {
          final List<VirtualFile> roots = ((NonClasspathClassFinder)finder).getClassRoots();
          for (VirtualFile root : roots) {
            if (VfsUtil.isAncestor(root, file, true)) {
              return NonClasspathDirectoryScope.compose(roots);
            }
          }
        }
      }
    }
    return null;
  }
}
