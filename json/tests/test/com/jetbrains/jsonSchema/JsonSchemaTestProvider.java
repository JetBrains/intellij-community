// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class JsonSchemaTestProvider implements JsonSchemaFileProvider {
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
}
