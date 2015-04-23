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

import com.intellij.reference.SoftLazyValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public interface StreamApiConstants {
  String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";

  String ANY_MATCH = "anyMatch";
  String ALL_MATCH = "allMatch";
  String MAP = "map";
  String FILTER = "filter";
  String FOR_EACH = "forEach";
  String FIND_FIRST = "findFirst";
  String LIMIT = "limit";
  String FLAT_MAP = "flatMap";

  String FAKE_FIND_MATCHED = "#findMatched";
  String FAKE_FIND_MATCHED_PATTERN = "filter(%s).findFirst().get()";
  String FAKE_FIND_MATCHED_WITH_DEFAULT_PATTERN = "filter(%s).findFirst().orElseGet(() -> %s)";

  String JAVA_UTIL_STREAM_COLLECTORS = "java.util.stream.Collectors";

  SoftLazyValue<Set<String>> STREAM_STREAM_API_METHODS = new SoftLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      return ContainerUtil.newLinkedHashSet(MAP, FILTER, FOR_EACH, ANY_MATCH, ALL_MATCH, FIND_FIRST, LIMIT, FLAT_MAP);
    }
  };

  String SKIP = "skip";
  String TO_ARRAY = "toArray";
}
