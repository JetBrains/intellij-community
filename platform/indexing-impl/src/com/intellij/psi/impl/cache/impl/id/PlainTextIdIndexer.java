// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PlainTextIdIndexer implements IdIndexer {
  private static final Key<Map<IdIndexEntry, Integer>> ID_INDEX_DATA_KEY = Key.create("plain.text.id.index");

  @Override
  @NotNull
  public Map<IdIndexEntry, Integer> map(@NotNull final FileContent inputData) {
    return getIdIndexData(inputData);
  }

  @NotNull
  public static Map<IdIndexEntry, Integer> getIdIndexData(@NotNull FileContent content) {
    Map<IdIndexEntry, Integer> idIndexData = content.getUserData(ID_INDEX_DATA_KEY);
    if (idIndexData != null) {
      content.putUserData(ID_INDEX_DATA_KEY, null);
      return idIndexData;
    }

    IdDataConsumer consumer = new IdDataConsumer();
    CharSequence text = content.getContentAsText();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      @Override
      public void run(final CharSequence chars11, char @Nullable [] charsArray, final int start, final int end) {
        if (charsArray != null) {
          consumer.addOccurrence(charsArray, start, end, UsageSearchContext.IN_PLAIN_TEXT);
        }
        else {
          consumer.addOccurrence(chars11, start, end, UsageSearchContext.IN_PLAIN_TEXT);
        }
      }
    }, text, 0, text.length());

    Map<IdIndexEntry, Integer> result = consumer.getResult();

    if (TodoIndexers.needsTodoIndex(content) &&
        IdIndex.isIndexable(PlainTextFileType.INSTANCE)) {
      content.putUserData(ID_INDEX_DATA_KEY, result);
    }

    return result;
  }
}
