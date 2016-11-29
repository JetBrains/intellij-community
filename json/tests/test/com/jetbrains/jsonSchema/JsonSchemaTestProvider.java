package com.jetbrains.jsonSchema;


import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;

public class JsonSchemaTestProvider implements JsonSchemaFileProvider {
  private final VirtualFile mySchemaFile;

  public JsonSchemaTestProvider(VirtualFile schemaFile) {
    mySchemaFile = schemaFile;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull VirtualFile file) {
    return file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "test";
  }

  @Override
  public VirtualFile getSchemaFile() {
    return mySchemaFile;
  }

  @Override
  public SchemaType getSchemaType() {
    return SchemaType.userSchema;
  }

  @Override
  public int getOrder() {
    return Orders.TEST;
  }
}
