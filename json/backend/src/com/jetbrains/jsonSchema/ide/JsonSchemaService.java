// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface JsonSchemaService {
  final class Impl {
    public static JsonSchemaService get(@NotNull Project project) {
      return project.getService(JsonSchemaService.class);
    }
  }

  static boolean isSchemaFile(@NotNull PsiFile psiFile) {
    if (JsonLikePsiWalker.getWalker(psiFile, JsonSchemaObjectReadingUtils.NULL_OBJ) == null) return false;
    final VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    JsonSchemaService service = Impl.get(psiFile.getProject());
    return service.isSchemaFile(file) && service.isApplicableToFile(file);
  }

  boolean isSchemaFile(@NotNull VirtualFile file);
  boolean isSchemaFile(@NotNull JsonSchemaObject schemaObject);

  @NotNull Project getProject();

  @Nullable
  JsonSchemaVersion getSchemaVersion(@NotNull VirtualFile file);

  @NotNull
  Collection<VirtualFile> getSchemaFilesForFile(@NotNull VirtualFile file);

  @Nullable
  VirtualFile getDynamicSchemaForFile(@NotNull PsiFile psiFile);
  void registerRemoteUpdateCallback(@NotNull Runnable callback);
  void unregisterRemoteUpdateCallback(@NotNull Runnable callback);
  void registerResetAction(Runnable action);
  void unregisterResetAction(Runnable action);

  void registerReference(String ref);
  boolean possiblyHasReference(String ref);

  void triggerUpdateRemote();

  @Nullable
  JsonSchemaObject getSchemaObject(@NotNull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObject(@NotNull PsiFile file);

  @Nullable
  JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile);

  @Nullable
  VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(final @NotNull VirtualFile schemaFile);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(final @NotNull JsonSchemaObject schemaObject);

  @Nullable
  VirtualFile resolveSchemaFile(final @NotNull JsonSchemaObject schemaObject);

  void reset();

  List<JsonSchemaInfo> getAllUserVisibleSchemas();

  boolean isApplicableToFile(@Nullable VirtualFile file);

  @NotNull JsonSchemaCatalogManager getCatalogManager();
}
