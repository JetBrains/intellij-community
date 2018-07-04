package com.jetbrains.jsonSchema;


import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonSchemaTestProvider implements JsonSchemaFileProvider {
  private final VirtualFile mySchemaFile;
  private final Predicate<? super VirtualFile> myAvailabilityPredicate;

  public JsonSchemaTestProvider(VirtualFile schemaFile, Predicate<? super VirtualFile> availabilityPredicate) {
    mySchemaFile = schemaFile;
    myAvailabilityPredicate = availabilityPredicate;
  }

  @Override
  public boolean isAvailable(@NotNull VirtualFile file) {
    return myAvailabilityPredicate.apply(file);
  }

  @NotNull
  @Override
  public String getName() {
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
