// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) return null;
    PsiJavaFile javaFile = ObjectUtils.tryCast(PsiManager.getInstance(project).findFile(file), PsiJavaFile.class);
    if (javaFile == null) return null;
    PsiJavaModule moduleDescriptor = javaFile.getModuleDeclaration();
    if (moduleDescriptor == null) return null;
    return fileName + " (" + moduleDescriptor.getName() + ")";
  }
}
