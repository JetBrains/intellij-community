// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Belyaev
 */
public final class ClasspathEntryMacro extends Macro {
  @Override
  public String getName() {
    return "ClasspathEntry";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.classpath.entry");
  }

  @Override
  public String expand(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;
    final VirtualFile classRoot = ProjectRootManager.getInstance(project).getFileIndex().getClassRootForFile(file);
    if (classRoot == null) return null;
    return getPath(classRoot);
  }
}