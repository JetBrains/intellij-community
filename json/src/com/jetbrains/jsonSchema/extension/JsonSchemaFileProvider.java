package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaFileProvider {
  boolean isAvailable(@NotNull VirtualFile file);

  @NotNull
  String getName();

  @Nullable
  VirtualFile getSchemaFile();

  @NotNull
  SchemaType getSchemaType();

  default JsonSchemaVersion getSchemaVersion() {
    return JsonSchemaVersion.SCHEMA_4;
  }

  @Nullable
  default String getThirdPartyApiInformation() {
    return null;
  }
}
