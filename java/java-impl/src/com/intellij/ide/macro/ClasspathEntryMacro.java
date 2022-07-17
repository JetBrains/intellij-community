// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Belyaev
 */
public final class ClasspathEntryMacro extends Macro implements PathMacro {
  @NotNull
  @Override
  public String getName() {
    return "ClasspathEntry";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("macro.classpath.entry");
  }

  @Override
  public String expand(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;
    final VirtualFile classRoot = ProjectRootManager.getInstance(project).getFileIndex().getClassRootForFile(file);
    if (classRoot == null) return null;
    return getPath(classRoot);
  }
}