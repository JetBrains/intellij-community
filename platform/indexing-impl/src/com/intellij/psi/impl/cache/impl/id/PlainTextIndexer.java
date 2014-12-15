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
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PlainTextIndexer extends FileTypeIdIndexer {
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
