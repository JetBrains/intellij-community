/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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

  SoftLazyValue<Set<String>> STREAM_STREAM_API_METHODS = new SoftLazyValue<>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      return ContainerUtil.newLinkedHashSet(MAP, FILTER, FOR_EACH, ANY_MATCH, ALL_MATCH, FIND_FIRST, LIMIT, FLAT_MAP);
    }
  };

  @NlsSafe String SKIP = "skip";
  @NlsSafe String TO_ARRAY = "toArray";
}
