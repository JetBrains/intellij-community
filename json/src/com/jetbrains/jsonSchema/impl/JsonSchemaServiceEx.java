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
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public interface JsonSchemaServiceEx extends JsonSchemaService {

  void visitSchemaObject(@NotNull VirtualFile schemaFile, @NotNull Processor<JsonSchemaObject> consumer);

  //! the only point for refreshing json schema caches
  void dropProviderFromCache(@NotNull VirtualFile schemaFile);

  @Nullable
  VirtualFile getSchemaFileById(@NotNull String id, VirtualFile referent);

  @Nullable
  Collection<Pair<VirtualFile, String>> getSchemaFilesByFile(@NotNull final VirtualFile file);

  Set<VirtualFile> getSchemaFiles();

  void refreshSchemaIds(Set<VirtualFile> toRefresh);
}
