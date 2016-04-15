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
}
