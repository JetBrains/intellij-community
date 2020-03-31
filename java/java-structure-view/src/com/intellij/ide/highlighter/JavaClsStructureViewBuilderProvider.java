// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClsStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull FileType fileType, @NotNull VirtualFile file, @NotNull Project project) {
    if (fileType == JavaClassFileType.INSTANCE) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

      if (psiFile instanceof ClsFileImpl) {
        PsiFile mirror = ((ClsFileImpl)psiFile).getCachedMirror();
        if (mirror != null) psiFile = mirror;
      }

      if (psiFile != null) {
        PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(psiFile.getLanguage());
        if (factory != null) {
          return factory.getStructureViewBuilder(psiFile);
        }
      }
    }

    return null;
  }
}