/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.StringPattern;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.id.PlainTextIdIndexer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlainTextTodoIndexer extends VersionedTodoIndexer {
  private static final Logger LOG = Logger.getInstance(PlainTextTodoIndexer.class);

  @Override
  @NotNull
  public Map<TodoIndexEntry, Integer> map(@NotNull final FileContent inputData) {
    final IndexPattern[] indexPatterns = IndexPatternUtil.getIndexPatterns();
    if (indexPatterns.length <= 0) return Collections.emptyMap();

    String chars = inputData.getContentAsText().toString(); // matching strings is faster than HeapCharBuffer
    OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
    for (IndexPattern indexPattern : indexPatterns) {
      if (!idIndexContainAllWords(indexPattern.getWordsToFindFirst(), indexPattern.isCaseSensitive(), inputData)) {
        continue;
      }

      Pattern pattern = indexPattern.getOptimizedIndexingPattern();
      try {
        if (pattern != null) {
          Matcher matcher = pattern.matcher(StringPattern.newBombedCharSequence(chars));
          while (matcher.find()) {
            if (matcher.start() != matcher.end()) {
              occurrenceConsumer.incTodoOccurrence(indexPattern);
            }
          }
        }
      }
      catch (StackOverflowError error) {
        LOG.error(error);
      }
    }
    Map<TodoIndexEntry, Integer> map = new HashMap<>();
    for (IndexPattern indexPattern : indexPatterns) {
      final int count = occurrenceConsumer.getOccurrenceCount(indexPattern);
      if (count > 0) {
        map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
      }
    }
    return map;
  }

  private static boolean idIndexContainAllWords(@NotNull List<String> words, boolean caseSensitive, @NotNull FileContent content) {
    Map<IdIndexEntry, Integer> idIndexData = PlainTextIdIndexer.getIdIndexData(content);
    return words.stream().allMatch(word -> idIndexData.containsKey(new IdIndexEntry(word, caseSensitive)));
  }
}
