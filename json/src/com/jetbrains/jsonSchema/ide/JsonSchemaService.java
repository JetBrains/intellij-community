package com.jetbrains.jsonSchema.ide;


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface JsonSchemaService {
  class Impl {
    public static JsonSchemaService get(@NotNull Project project) {
      return ServiceManager.getService(project, JsonSchemaService.class);
    }
  }

  @NotNull
  Collection<VirtualFile> getSchemaFilesForFile(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaForCodeAssistance(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile);

  @Nullable
  VirtualFile getSchemaFileById(@NotNull String id, VirtualFile referent);

  Set<VirtualFile> getSchemaFiles();

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(@NotNull final VirtualFile schemaFile);

  void reset();
}
