// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public interface StreamApiConstants {
  String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";

  @NlsSafe String ANY_MATCH = "anyMatch";
  @NlsSafe String ALL_MATCH = "allMatch";
  @NlsSafe String MAP = "map";
  @NlsSafe String FILTER = "filter";
  @NlsSafe String FOR_EACH = "forEach";
  @NlsSafe String FIND_FIRST = "findFirst";
  @NlsSafe String LIMIT = "limit";
  @NlsSafe String FLAT_MAP = "flatMap";

  @NlsSafe String FAKE_FIND_MATCHED = "#findMatched";
  @NlsSafe String FAKE_FIND_MATCHED_PATTERN = "filter(%s).findFirst().get()";
  @NlsSafe String FAKE_FIND_MATCHED_WITH_DEFAULT_PATTERN = "filter(%s).findFirst().orElseGet(() -> %s)";

  @NlsSafe String JAVA_UTIL_STREAM_COLLECTORS = "java.util.stream.Collectors";

  Supplier<Set<String>> STREAM_STREAM_API_METHODS = NotNullLazyValue.softLazy(() -> {
    return ContainerUtil.newLinkedHashSet(MAP, FILTER, FOR_EACH, ANY_MATCH, ALL_MATCH, FIND_FIRST, LIMIT, FLAT_MAP);
  });

  @NlsSafe String SKIP = "skip";
  @NlsSafe String TO_ARRAY = "toArray";
}
