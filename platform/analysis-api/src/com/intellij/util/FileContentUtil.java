// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public final class FileContentUtil {

  public static void reparseFiles(final @NotNull Project project, final @NotNull Collection<? extends VirtualFile> files, final boolean includeOpenFiles) {
    LinkedHashSet<VirtualFile> fileSet = new LinkedHashSet<>(files);
    if (includeOpenFiles) {
      Collections.addAll(fileSet, FileEditorManager.getInstance(project).getOpenFiles());
    }
    FileContentUtilCore.reparseFiles(fileSet);
  }

  public static void reparseOpenedFiles() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      reparseFiles(project, Collections.emptyList(), true);
    }
  }
}
