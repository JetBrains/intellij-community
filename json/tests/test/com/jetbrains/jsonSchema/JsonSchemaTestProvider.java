package com.jetbrains.jsonSchema;


import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.StringReader;

public class JsonSchemaTestProvider implements JsonSchemaFileProvider<String> {

  private final String mySchemaText;

  public JsonSchemaTestProvider(String text) {
    mySchemaText = text;
  }

  @Override
  public boolean isAvailable(@NotNull VirtualFile file) {
    return file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(JsonLanguage.INSTANCE);
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

  @NotNull
  @Override
  public Pair<SchemaType, String> getKey() {
    return Pair.create(SchemaType.userSchema, mySchemaText);
  }
}
