// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  /**
   * @see JsonSchemaProviderFactory#getResourceFile(Class, String)
   */
  @Nullable
  VirtualFile getSchemaFile();

  @NotNull
  SchemaType getSchemaType();

  default JsonSchemaVersion getSchemaVersion() {
    return JsonSchemaVersion.SCHEMA_4;
  }

  /**
   * Information about the provided API (e.g., an API version or the target platform).
   * This is useful for auto-generated schemas targeting multiple versions of the same config.
   */
  default @Nullable @Nls String getThirdPartyApiInformation() {
    return null;
  }

  /**
   * @return {@code true} if this schema is shown and selectable by the user in the schema dropdown menu.
   * Some schemas are designed to be auto-assigned and bound to very particular contexts,
   * and thus hidden from the selector.
   */
  default boolean isUserVisible() { return true; }

  /**
   * Presentable name of the schema shown in the UI.
   */
  default @NotNull @NlsContexts.ListItem String getPresentableName() { return getName(); }

  /**
   * A URL to download an up-to-date schema version.
   * <p>
   * It's recommended to have it matching the URL for the schema in the SchemaStore catalogue.
   * If for some reason you need to have a unique URL for your schema, which is not a schema file (for example, its website),
   * you need to postfix it with '!'.
   */
  default @Nullable @NonNls String getRemoteSource() { return null; }
}
