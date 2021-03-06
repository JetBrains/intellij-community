// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public final class JsonPathConstants {
  private JsonPathConstants() {
  }

  public static final List<String> STANDARD_NAMED_OPERATORS = List.of(
    "in", "nin", "subsetof", "anyof", "noneof", "size", "empty", "contains"
  );

  public static final Map<String, String> STANDARD_FUNCTIONS = ImmutableMap.<String, String>builder()
    .put("concat", "string")
    .put("keys", "array")
    .put("length", "number")
    .put("min", "number")
    .put("max", "number")
    .put("avg", "number")
    .put("stddev", "number")
    .put("sum", "number")
    .build();
}
