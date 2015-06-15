/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class JavaClsStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull FileType fileType, @NotNull VirtualFile file, @NotNull Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    if (fileType == JavaClassFileType.INSTANCE && psiFile != null) {
      Language language = psiFile.getLanguage();
      if (language != JavaLanguage.INSTANCE) {
        PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(language);
        if (factory != null) {
          return factory.getStructureViewBuilder(psiFile);
        }
      }
    }

    if (psiFile instanceof PsiClassOwner) {
      return new TreeBasedStructureViewBuilder() {
        @Override
        @NotNull
        public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
          return new JavaFileTreeModel((PsiClassOwner)psiFile, editor);
        }
      };
    }

    return null;
  }
}