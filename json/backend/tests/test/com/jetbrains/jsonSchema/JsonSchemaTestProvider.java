// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonSchemaTestProvider implements JsonSchemaFileProvider {
  // Plain-text search for the `$schema` URL — sufficient for the unit-test schemas this
  // provider serves. Avoids depending on Project-scoped helpers like JsonCachedValues.
  private static final Pattern SCHEMA_URL_PATTERN = Pattern.compile("\"\\$schema\"\\s*:\\s*\"([^\"]+)\"");

  private final VirtualFile mySchemaFile;
  private final Predicate<? super VirtualFile> myAvailabilityPredicate;

  public JsonSchemaTestProvider(VirtualFile schemaFile, Predicate<? super VirtualFile> availabilityPredicate) {
    mySchemaFile = schemaFile;
    myAvailabilityPredicate = availabilityPredicate;
  }

  @Override
  public boolean isAvailable(@NotNull VirtualFile file) {
    return myAvailabilityPredicate.test(file);
  }

  @NotNull
  @Override
  public @NlsSafe String getName() {
    return "test";
  }

  @Nullable
  @Override
  public VirtualFile getSchemaFile() {
    return mySchemaFile;
  }

  @NotNull
  @Override
  public SchemaType getSchemaType() {
    return SchemaType.userSchema;
  }

  @Override
  public JsonSchemaVersion getSchemaVersion() {
    if (mySchemaFile != null) {
      try {
        String text = new String(mySchemaFile.contentsToByteArray(), mySchemaFile.getCharset());
        Matcher matcher = SCHEMA_URL_PATTERN.matcher(text);
        if (matcher.find()) {
          JsonSchemaVersion fromUrl = JsonSchemaVersion.byId(matcher.group(1));
          if (fromUrl != null) return fromUrl;
        }
      }
      catch (IOException ignored) {
      }
    }
    return JsonSchemaFileProvider.super.getSchemaVersion();
  }
}
