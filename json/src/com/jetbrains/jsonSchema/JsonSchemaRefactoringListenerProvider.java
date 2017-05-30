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
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 2/17/2016.
 */
public class JsonSchemaRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (element == null) {
      return null;
    }
    final VirtualFile oldFile = PsiUtilBase.asVirtualFile(element);
    if (oldFile == null || !(oldFile.getFileType() instanceof LanguageFileType) ||
      !(((LanguageFileType)oldFile.getFileType()).getLanguage().isKindOf(JsonLanguage.INSTANCE))) {
      return null;
    }
    final Project project = element.getProject();
    if (project.getBaseDir() == null) return null;

    final String oldRelativePath = VfsUtilCore.getRelativePath(oldFile, project.getBaseDir());
    if (oldRelativePath != null) {
      final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
      return new UndoRefactoringElementAdapter() {
        @Override
        protected void refactored(@NotNull PsiElement element, @Nullable String oldQualifiedName) {
          final VirtualFile newFile = PsiUtilBase.asVirtualFile(element);
          if (newFile != null) {
            final String newRelativePath = VfsUtilCore.getRelativePath(newFile, project.getBaseDir());
            if (newRelativePath != null) {
              configuration.schemaFileMoved(project, oldRelativePath, newRelativePath);
            }
          }
        }
      };
    }
    return null;
  }
}
