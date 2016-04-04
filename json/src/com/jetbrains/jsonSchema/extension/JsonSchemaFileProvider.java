package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;

public interface JsonSchemaFileProvider {
  boolean isAvailable(@NotNull VirtualFile file);

  @Nullable
  Reader getSchemaReader();

  @NotNull
  String getName();
}
