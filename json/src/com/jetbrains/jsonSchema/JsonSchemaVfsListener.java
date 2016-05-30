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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 3/30/2016.
 */
public class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public JsonSchemaVfsListener(Project project, @NotNull final JsonSchemaServiceImpl service) {
    super(new VirtualFileAdapter() {
      @NotNull private final JsonSchemaServiceImpl myService = service;
      private JsonSchemaMappingsProjectConfiguration myMappingsProjectConfiguration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        onFileChange(event.getFile());
      }

      private void onFileChange(@NotNull final VirtualFile file) {
        if (myMappingsProjectConfiguration.isRegisteredSchemaFile(file)) {
          myService.reset();
        }
      }
    });
  }
}
