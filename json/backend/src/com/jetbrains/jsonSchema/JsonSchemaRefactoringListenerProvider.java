// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class JsonSchemaRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Override
  public @Nullable RefactoringElementListener getListener(PsiElement element) {
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
