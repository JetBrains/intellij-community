package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface JsonSchemaFileProvider {
  boolean isAvailable(@NotNull Project project, @NotNull VirtualFile file);

  @NotNull
  String getName();

  VirtualFile getSchemaFile();

  SchemaType getSchemaType();

  int getOrder();

  interface Orders {
    int CORE = -1000;
    int EMBEDDED_BASE = 1;
    int PACKAGE_JSON = 2;
    int TEST = 10;
    int USER = 1000;
  }
}
