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
import com.intellij.openapi.vfs.VfsUtil;
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
    VirtualFile fileAtElement = PsiUtilBase.asVirtualFile(element);
    if (fileAtElement == null || !(fileAtElement.getFileType() instanceof LanguageFileType) ||
      !(((LanguageFileType)fileAtElement.getFileType()).getLanguage().isKindOf(JsonLanguage.INSTANCE))) {
      return null;
    }
    Project project = element.getProject();
    final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
    final JsonSchemaMappingsConfigurationBase.SchemaInfo schemaInfo = configuration.getSchemaBySchemaFile(fileAtElement);
    if (schemaInfo != null && project.getBaseDir() != null) {
      return new UndoRefactoringElementAdapter() {
        @Override
        protected void refactored(@NotNull PsiElement element, @Nullable String oldQualifiedName) {
          VirtualFile newFile = PsiUtilBase.asVirtualFile(element);
          if (newFile != null) {
            final String relativePath = VfsUtil.getRelativePath(newFile, project.getBaseDir());
            if (relativePath != null) {
              configuration.removeSchema(schemaInfo);
              final JsonSchemaMappingsConfigurationBase.SchemaInfo newSchema =
                new JsonSchemaMappingsConfigurationBase.SchemaInfo(schemaInfo.getName(), relativePath, schemaInfo.isApplicationLevel(),
                                                                   schemaInfo.getPatterns());
              configuration.addSchema(newSchema);
            }
          }
        }
      };
    }
    return null;
  }
}
