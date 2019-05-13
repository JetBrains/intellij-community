/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author max
 */
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