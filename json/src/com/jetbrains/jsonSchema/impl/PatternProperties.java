// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.matchPattern;

public final class PatternProperties {
  public final @NotNull Map<String, JsonSchemaObject> mySchemasMap;
  public final @NotNull Map<String, Pattern> myCachedPatterns;
  public final @NotNull Map<String, String> myCachedPatternProperties;

  public @NotNull Map<String, JsonSchemaObject> getSchemasMap() {
    return mySchemasMap;
  }

  public PatternProperties(final @NotNull Map<String, ? extends JsonSchemaObject> schemasMap) {
    mySchemasMap = new HashMap<>();
    schemasMap.keySet().forEach(key -> mySchemasMap.put(StringUtil.unescapeBackSlashes(key), schemasMap.get(key)));
    myCachedPatterns = new HashMap<>();
    myCachedPatternProperties = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
    mySchemasMap.keySet().forEach(key -> {
      ProgressManager.checkCanceled();
      final Pair<Pattern, String> pair = JsonSchemaObjectReadingUtils.compilePattern(key);
      if (pair.getSecond() == null) {
        assert pair.getFirst() != null;
        myCachedPatterns.put(key, pair.getFirst());
      }
    });
  }

  public @Nullable JsonSchemaObject getPatternPropertySchema(final @NotNull String name) {
    String value = myCachedPatternProperties.get(name);
    if (value != null) {
      assert mySchemasMap.containsKey(value);
      return mySchemasMap.get(value);
    }

    value = myCachedPatterns.keySet().stream()
      .filter(key -> matchPattern(myCachedPatterns.get(key), name))
      .findFirst()
      .orElse(null);
    if (value != null) {
      myCachedPatternProperties.put(name, value);
      assert mySchemasMap.containsKey(value);
      return mySchemasMap.get(value);
    }
    return null;
  }
}