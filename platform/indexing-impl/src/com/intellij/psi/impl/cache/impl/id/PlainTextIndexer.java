// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PlainTextIndexer implements IdIndexer {
  @Override
  @NotNull
  public Map<IdIndexEntry, Integer> map(@NotNull final FileContent inputData) {
    final IdDataConsumer consumer = new IdDataConsumer();
    final CharSequence chars = inputData.getContentAsText();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      @Override
      public void run(final CharSequence chars11, @Nullable char[] charsArray, final int start, final int end) {
        if (charsArray != null) {
          consumer.addOccurrence(charsArray, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
        }
        else {
          consumer.addOccurrence(chars11, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
        }
      }
    }, chars, 0, chars.length());
    return consumer.getResult();
  }
}
