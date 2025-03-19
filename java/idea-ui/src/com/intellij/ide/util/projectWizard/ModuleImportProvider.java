// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class ModuleImportProvider extends ProjectImportProvider {

  public ModuleImportProvider() {
    super(new ModuleImportBuilder());
  }

  @Override
  public boolean canImport(@NotNull VirtualFile fileOrDirectory, Project project) {
    return project != null && !fileOrDirectory.isDirectory() && "iml".equals(fileOrDirectory.getExtension());
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }

  @Override
  public boolean canCreateNewProject() {
    return false;
  }

  @Override
  public @Nullable String getFileSample() {
    return JavaUiBundle.message("intellij.idea.module.file.iml");
  }
}
