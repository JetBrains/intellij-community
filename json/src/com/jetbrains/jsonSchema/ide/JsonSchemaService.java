package com.jetbrains.jsonSchema.ide;


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface JsonSchemaService {
  @NotNull
  static String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }

  class Impl {
    public static JsonSchemaService get(@NotNull Project project) {
      return ServiceManager.getService(project, JsonSchemaService.class);
    }
  }

  @NotNull
  Collection<VirtualFile> getSchemaFilesForFile(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObject(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile);

  @Nullable
  VirtualFile findSchemaFileByReference(@NotNull String reference, VirtualFile referent);

  @NotNull
  Set<VirtualFile> getSchemaFiles();

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(@NotNull final VirtualFile schemaFile);

  void reset();

  ModificationTracker getAnySchemaChangeTracker();
}
