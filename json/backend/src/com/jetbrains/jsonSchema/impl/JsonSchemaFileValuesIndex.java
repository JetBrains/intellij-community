// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonFileType;
import com.intellij.json.syntax.JsonSyntaxElementTypes;
import com.intellij.json.json5.Json5FileType;
import com.intellij.json.syntax.json5.Json5SyntaxLexer;
import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.element.SyntaxTokenTypes;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION;

public final class JsonSchemaFileValuesIndex extends FileBasedIndexExtension<String, String> {
  public static final ID<String, String> INDEX_ID = ID.create("json.file.root.values");
  private static final int VERSION = 5;
  public static final String NULL = "$NULL$";
  public static final String SCHEMA_PROPERTY_NAME = "$schema";

  @Override
  public @NotNull ID<String, String> getName() {
    return INDEX_ID;
  }

  private final DataIndexer<String, String, FileContent> myIndexer =
    new DataIndexer<>() {
      @Override
      public @NotNull Map<String, String> map(@NotNull FileContent inputData) {
        return readTopLevelProps(inputData.getFileType(), inputData.getContentAsText());
      }
    };

  @Override
  public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<String> getValueExternalizer() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new FileTypeInputFilterPredicate(BEFORE_SUBSTITUTION, fileType -> fileType instanceof JsonFileType);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static @Nullable String getCachedValue(Project project, VirtualFile file, String requestedKey) {
    if (project.isDisposed() || !file.isValid() || DumbService.isDumb(project)) return NULL;
    return FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project).get(requestedKey);
  }

  static @NotNull Map<String, String> readTopLevelProps(@NotNull FileType fileType, @NotNull CharSequence content) {
    if (!(fileType instanceof JsonFileType)) return new HashMap<>();

    Lexer lexer = fileType == Json5FileType.INSTANCE ?
                  new Json5SyntaxLexer() :
                  new JsonSyntaxLexer();
    final HashMap<String, String> map = new HashMap<>();
    lexer.start(content);

    // We only care about properties at the root level having the form of "property" : "value".
    int nesting = 0;
    boolean idFound = false;
    boolean obsoleteIdFound = false;
    boolean schemaFound = false;
    while (!(idFound && schemaFound && obsoleteIdFound) && lexer.getTokenStart() < lexer.getBufferEnd()) {
      SyntaxElementType token = lexer.getTokenType();
      // Nesting level can only change at curly braces.
      if (token == JsonSyntaxElementTypes.L_CURLY) {
        nesting++;
      }
      else if (token == JsonSyntaxElementTypes.R_CURLY) {
        nesting--;
      }
      else if (nesting == 1 &&
               (token == JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING
                || token == JsonSyntaxElementTypes.SINGLE_QUOTED_STRING
                || token == JsonSyntaxElementTypes.IDENTIFIER)) {
        // We are looking for two special properties at the root level.
        switch (lexer.getTokenText()) {
          case "$id", "\"$id\"", "'$id'" -> idFound |= captureValueIfString(lexer, map, JsonCachedValues.ID_CACHE_KEY);
          case "id", "\"id\"", "'id'" -> obsoleteIdFound |= captureValueIfString(lexer, map, JsonCachedValues.OBSOLETE_ID_CACHE_KEY);
          case SCHEMA_PROPERTY_NAME, "\"$schema\"", "'$schema'" -> schemaFound |= captureValueIfString(lexer, map, JsonCachedValues.URL_CACHE_KEY);
        }
      }
      lexer.advance();
    }
    if (!map.containsKey(JsonCachedValues.ID_CACHE_KEY)) map.put(JsonCachedValues.ID_CACHE_KEY, NULL);
    if (!map.containsKey(JsonCachedValues.OBSOLETE_ID_CACHE_KEY)) map.put(JsonCachedValues.OBSOLETE_ID_CACHE_KEY, NULL);
    if (!map.containsKey(JsonCachedValues.URL_CACHE_KEY)) map.put(JsonCachedValues.URL_CACHE_KEY, NULL);
    return map;
  }

  private static boolean captureValueIfString(@NotNull Lexer lexer, @NotNull HashMap<String, String> destMap, @NotNull String key) {
    SyntaxElementType token;
    lexer.advance();
    token = skipWhitespacesAndGetTokenType(lexer);
    if (token == JsonSyntaxElementTypes.COLON) {
      lexer.advance();
      token = skipWhitespacesAndGetTokenType(lexer);
      if (token == JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING || token == JsonSyntaxElementTypes.SINGLE_QUOTED_STRING) {
        String text = lexer.getTokenText();
        destMap.put(key, text.length() <= 1 ? "" : text.substring(1, text.length() - 1));
        return true;
      }
    }
    return false;
  }

  private static @Nullable SyntaxElementType skipWhitespacesAndGetTokenType(@NotNull Lexer lexer) {
    while ( lexer.getTokenType() == SyntaxTokenTypes.getWHITE_SPACE() ||
           lexer.getTokenType() == JsonSyntaxElementTypes.LINE_COMMENT ||
           lexer.getTokenType() == JsonSyntaxElementTypes.BLOCK_COMMENT) {
      lexer.advance();
    }
    return lexer.getTokenType();
  }
}
