// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLexer;
import com.intellij.json.json5.Json5FileType;
import com.intellij.json.json5.Json5Lexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class JsonSchemaFileValuesIndex extends FileBasedIndexExtension<String, String> {
  public static final ID<String, String> INDEX_ID = ID.create("json.file.root.values");
  private static final int VERSION = 5;
  public static final String NULL = "$NULL$";

  @NotNull
  @Override
  public ID<String, String> getName() {
    return INDEX_ID;
  }

  private final DataIndexer<String, String, FileContent> myIndexer =
    new DataIndexer<>() {
      @Override
      @NotNull
      public Map<String, String> map(@NotNull FileContent inputData) {
        return readTopLevelProps(inputData.getFileType(), inputData.getContentAsText());
      }
    };

  @NotNull
  @Override
  public DataIndexer<String, String, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> file.getFileType() instanceof JsonFileType;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Nullable
  public static String getCachedValue(Project project, VirtualFile file, String requestedKey) {
    if (project.isDisposed() || !file.isValid() || DumbService.isDumb(project)) return NULL;
    return FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project).get(requestedKey);
  }

  @NotNull
  static Map<String, String> readTopLevelProps(@NotNull FileType fileType, @NotNull CharSequence content) {
    if (!(fileType instanceof JsonFileType)) return new HashMap<>();

    Lexer lexer = fileType == Json5FileType.INSTANCE ? new Json5Lexer() : new JsonLexer();
    final HashMap<String, String> map = new HashMap<>();
    lexer.start(content);

    // We only care about properties at the root level having the form of "property" : "value".
    int nesting = 0;
    boolean idFound = false;
    boolean obsoleteIdFound = false;
    boolean schemaFound = false;
    while (!(idFound && schemaFound && obsoleteIdFound) && lexer.getTokenStart() < lexer.getBufferEnd()) {
      IElementType token = lexer.getTokenType();
      // Nesting level can only change at curly braces.
      if (token == JsonElementTypes.L_CURLY) {
        nesting++;
      }
      else if (token == JsonElementTypes.R_CURLY) {
        nesting--;
      }
      else if (nesting == 1 &&
               (token == JsonElementTypes.DOUBLE_QUOTED_STRING
                || token == JsonElementTypes.SINGLE_QUOTED_STRING
                || token == JsonElementTypes.IDENTIFIER)) {
        // We are looking for two special properties at the root level.
        switch (lexer.getTokenText()) {
          case "$id", "\"$id\"", "'$id'" -> idFound |= captureValueIfString(lexer, map, JsonCachedValues.ID_CACHE_KEY);
          case "id", "\"id\"", "'id'" -> obsoleteIdFound |= captureValueIfString(lexer, map, JsonCachedValues.OBSOLETE_ID_CACHE_KEY);
          case "$schema", "\"$schema\"", "'$schema'" -> schemaFound |= captureValueIfString(lexer, map, JsonCachedValues.URL_CACHE_KEY);
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
    IElementType token;
    lexer.advance();
    token = skipWhitespacesAndGetTokenType(lexer);
    if (token == JsonElementTypes.COLON) {
      lexer.advance();
      token = skipWhitespacesAndGetTokenType(lexer);
      if (token == JsonElementTypes.DOUBLE_QUOTED_STRING || token == JsonElementTypes.SINGLE_QUOTED_STRING) {
        String text = lexer.getTokenText();
        destMap.put(key, text.length() <= 1 ? "" : text.substring(1, text.length() - 1));
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static IElementType skipWhitespacesAndGetTokenType(@NotNull Lexer lexer) {
    while (lexer.getTokenType() == TokenType.WHITE_SPACE ||
           lexer.getTokenType() == JsonElementTypes.LINE_COMMENT ||
           lexer.getTokenType() == JsonElementTypes.BLOCK_COMMENT) {
      lexer.advance();
    }
    return lexer.getTokenType();
  }
}
