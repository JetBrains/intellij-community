// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) {
      return null;
    }

    return ReadAction.compute(() -> {
      Object obj = PsiManager.getInstance(project).findFile(file);
      PsiJavaFile javaFile = obj instanceof PsiJavaFile ? (PsiJavaFile)obj : null;
      PsiJavaModule moduleDescriptor = javaFile == null ? null : javaFile.getModuleDeclaration();
      if (moduleDescriptor == null) {
        return null;
      }
      return fileName + " (" + moduleDescriptor.getName() + ")";
    });
  }
}
