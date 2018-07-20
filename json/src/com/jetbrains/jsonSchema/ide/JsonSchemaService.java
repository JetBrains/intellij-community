// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.ide;


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface JsonSchemaService {
  class Impl {
    public static JsonSchemaService get(@NotNull Project project) {
      return ServiceManager.getService(project, JsonSchemaService.class);
    }
  }

  static boolean isSchemaFile(@NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    JsonSchemaService service = Impl.get(psiFile.getProject());
    return service.isApplicableToFile(file) && service.isSchemaFile(file);
  }

  boolean isSchemaFile(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaVersion getSchemaVersion(@NotNull VirtualFile file);

  @NotNull
  Collection<VirtualFile> getSchemaFilesForFile(@NotNull VirtualFile file);

  void registerRemoteUpdateCallback(Runnable callback);
  void unregisterRemoteUpdateCallback(Runnable callback);
  void registerResetAction(Runnable action);
  void unregisterResetAction(Runnable action);

  void triggerUpdateRemote();

  @Nullable
  JsonSchemaObject getSchemaObject(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile);

  @Nullable
  VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(@NotNull final VirtualFile schemaFile);

  void reset();

  ModificationTracker getAnySchemaChangeTracker();

  List<JsonSchemaInfo> getAllUserVisibleSchemas();

  boolean isApplicableToFile(@Nullable VirtualFile file);

  @NotNull
  static String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }
}
