// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;


import com.intellij.json.JsonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonSchemaReader {
  private static final int MAX_SCHEMA_LENGTH = FileSizeLimit.getDefaultContentLoadLimit();

  public static @NotNull JsonSchemaObject readFromFile(@NotNull Project project, @NotNull VirtualFile file) throws Exception {
    if (!file.isValid()) {
      throw new Exception(JsonBundle.message("schema.reader.cant.load.file", file.getName()));
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    JsonSchemaObject object = psiFile == null ? null : new JsonSchemaReader().read(psiFile);
    if (object == null) {
      throw new Exception(JsonBundle.message("schema.reader.cant.load.model", file.getName()));
    }
    return object;
  }

  public static @Nullable @DialogMessage String checkIfValidJsonSchema(@NotNull Project project, @NotNull VirtualFile file) {
    final long length = file.getLength();
    final String fileName = file.getName();
    if (length > MAX_SCHEMA_LENGTH) {
      return JsonBundle.message("schema.reader.file.too.large", fileName, length);
    }
    if (length == 0) {
      return JsonBundle.message("schema.reader.file.empty", fileName);
    }
    try {
      readFromFile(project, file);
    }
    catch (Exception e) {
      final String message = JsonBundle.message("schema.reader.file.not.found.or.error", fileName, e.getMessage());
      Logger.getInstance(JsonSchemaReader.class).info(message);
      return message;
    }
    return null;
  }

  public static @Nullable JsonSchemaObject getOrComputeSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile, @NotNull Project project) {
    return JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile);
  }

  public @Nullable JsonSchemaObject read(@NotNull PsiFile file) {
    return getOrComputeSchemaObjectForSchemaFile(file.getOriginalFile().getVirtualFile(), file.getProject());
  }

  public static @NotNull String getNewPointer(@NotNull String name, String oldPointer) {
    return oldPointer.equals("/") ? oldPointer + name : oldPointer + "/" + name;
  }

  public static @Nullable JsonSchemaType parseType(final @NotNull String typeString) {
    try {
      return JsonSchemaType.valueOf("_" + typeString);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }
}
