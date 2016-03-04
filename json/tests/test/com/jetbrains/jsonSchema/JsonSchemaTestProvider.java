package com.jetbrains.jsonSchema;


import com.intellij.json.JsonFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.StringReader;

public class JsonSchemaTestProvider implements JsonSchemaFileProvider {

  private final String mySchemaText;

  public JsonSchemaTestProvider(String text) {
    mySchemaText = text;
  }

  @Override
  public boolean isAvailable(@NotNull VirtualFile file) {
    return file.getFileType() == JsonFileType.INSTANCE;
  }

  @Nullable
  @Override
  public Reader getSchemaReader() {
    return new StringReader(mySchemaText);
  }

  @NotNull
  @Override
  public String getName() {
    return "test";
  }
}
