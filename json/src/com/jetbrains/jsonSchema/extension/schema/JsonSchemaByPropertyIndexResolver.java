/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.jsonSchema.impl.JsonSchemaResourcesRootsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 1/10/2017.
 */
public class JsonSchemaByPropertyIndexResolver {
  @NotNull private final String myReferenceName;
  @NotNull private final Project myProject;
  @Nullable private final VirtualFile mySchemaFile;

  private VirtualFile myFile;
  private Integer myOffset;

  public JsonSchemaByPropertyIndexResolver(@NotNull String referenceName,
                                           @NotNull Project project,
                                           @Nullable VirtualFile schemaFile) {
    myReferenceName = referenceName;
    myProject = project;
    mySchemaFile = schemaFile;
  }

  public PsiElement resolveByName() {
    final GlobalSearchScope scope;
    if (mySchemaFile != null) {
      scope = GlobalSearchScope.fileScope(myProject, mySchemaFile);
    } else {
      scope = JsonSchemaResourcesRootsProvider.enlarge(myProject, GlobalSearchScope.allScope(myProject));
    }

    FileBasedIndex.getInstance().processValues(JsonSchemaFileIndex.PROPERTIES_INDEX, myReferenceName, null, (file, value) -> {
      if (!scope.contains(file)) return true;
      myFile = file;
      myOffset = value;
      return false;
    }, scope);

    if (myFile != null) {
      myOffset = myOffset == null ? 0 : myOffset;
      final PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
      if (file != null) {
        return file.findElementAt(myOffset);
      }
    }
    return null;
  }
}
