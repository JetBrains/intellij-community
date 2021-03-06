package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaFileProvider {
  boolean isAvailable(@NotNull VirtualFile file);

  @NotNull
  @Nls
  String getName();

  @Nullable
  VirtualFile getSchemaFile();

  @NotNull
  SchemaType getSchemaType();

  default JsonSchemaVersion getSchemaVersion() {
    return JsonSchemaVersion.SCHEMA_4;
  }

  @Nullable
  @Nls
  default String getThirdPartyApiInformation() {
    return null;
  }

  default boolean isUserVisible() { return true; }

  @NotNull
  @NlsContexts.ListItem
  default String getPresentableName() { return getName(); }

  @Nullable
  @NonNls
  default String getRemoteSource() { return null; }
}
