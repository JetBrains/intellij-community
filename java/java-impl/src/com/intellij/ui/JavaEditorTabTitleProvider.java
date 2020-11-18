// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) return null;
    PsiJavaModule moduleDescriptor = JavaModuleGraphUtil.findDescriptorByFile(file, project);
    if (moduleDescriptor == null) return null;
    return fileName + " (" + moduleDescriptor.getName() + ")";
  }
}
