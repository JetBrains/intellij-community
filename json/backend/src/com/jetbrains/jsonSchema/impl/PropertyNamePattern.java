// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.matchPattern;

public final class PropertyNamePattern {
  public final @NotNull String myPattern;
  public final @Nullable Pattern myCompiledPattern;
  public final @Nullable String myPatternError;
  public final @NotNull Map<String, Boolean> myValuePatternCache;

  public PropertyNamePattern(@NotNull String pattern) {
    myPattern = StringUtil.unescapeBackSlashes(pattern);
    final Pair<Pattern, String> pair = JsonSchemaObjectReadingUtils.compilePattern(pattern);
    myPatternError = pair.getSecond();
    myCompiledPattern = pair.getFirst();
    myValuePatternCache = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
  }

  public @Nullable String getPatternError() {
    return myPatternError;
  }

  public boolean checkByPattern(final @NotNull String name) {
    if (myPatternError != null) return true;
    if (Boolean.TRUE.equals(myValuePatternCache.get(name))) return true;
    assert myCompiledPattern != null;
    boolean matches = matchPattern(myCompiledPattern, name);
    myValuePatternCache.put(name, matches);
    return matches;
  }

  public @NotNull String getPattern() {
    return myPattern;
  }
}
