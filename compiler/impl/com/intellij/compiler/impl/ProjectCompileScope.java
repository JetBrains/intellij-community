/*
 * @author: Eugene Zhuravlev
 * Date: Jan 20, 2003
 * Time: 5:34:19 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

public class ProjectCompileScope extends FileIndexCompileScope {
  private final Project myProject;
  private final String myTempDirUrl;

  public ProjectCompileScope(final Project project) {
    myProject = project;
    final String path = CompilerPaths.getCompilerSystemDirectory(project).getPath().replace(File.separatorChar, '/');
    myTempDirUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path) + '/';
  }

  protected FileIndex[] getFileIndices() {
    return new FileIndex[] {ProjectRootManager.getInstance(myProject).getFileIndex()};
  }

  public boolean belongs(String url) {
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      for (FileIndex index : getFileIndices()) {
        if (index.isInSourceContent(file)) {
          return true;
        }
      }
    }
    else {
      // the file might be deleted
      for (VirtualFile root : ProjectRootManager.getInstance(myProject).getContentSourceRoots()) {
        final String rootUrl = root.getUrl();
        if (FileUtil.startsWith(url, rootUrl.endsWith("/")? rootUrl : rootUrl + "/")) {
          return true;
        }
      }
    }
    return false;
    //return !FileUtil.startsWith(url, myTempDirUrl);
  }

  public Module[] getAffectedModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }
}
