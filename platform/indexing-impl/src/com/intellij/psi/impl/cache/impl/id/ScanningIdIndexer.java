// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.lang.cacheBuilder.VersionedWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public abstract class ScanningIdIndexer implements IdIndexer {

  protected abstract WordsScanner createScanner();

  @Override
  @NotNull
  public Map<IdIndexEntry, Integer> map(@NotNull final FileContent inputData) {
    final CharSequence chars = inputData.getContentAsText();
    final char[] charsArray = CharArrayUtil.fromSequenceWithoutCopying(chars);
    final IdDataConsumer consumer = new IdDataConsumer();
    createScanner().processWords(chars, new Processor<WordOccurrence>() {
      @Override
      public boolean process(final WordOccurrence t) {
        if (charsArray != null && t.getBaseText() == chars) {
          consumer.addOccurrence(charsArray, t.getStart(), t.getEnd(), convertToMask(t.getKind()));
        }
        else {
          consumer.addOccurrence(t.getBaseText(), t.getStart(), t.getEnd(), convertToMask(t.getKind()));
        }
        return true;
      }

      private int convertToMask(final WordOccurrence.Kind kind) {
        if (kind == null) {
          return UsageSearchContext.ANY;
        }
        if (kind == WordOccurrence.Kind.CODE) return UsageSearchContext.IN_CODE;
        if (kind == WordOccurrence.Kind.COMMENTS) return UsageSearchContext.IN_COMMENTS;
        if (kind == WordOccurrence.Kind.LITERALS) return UsageSearchContext.IN_STRINGS;
        if (kind == WordOccurrence.Kind.FOREIGN_LANGUAGE) return UsageSearchContext.IN_FOREIGN_LANGUAGES;
        return 0;
      }
    });
    return consumer.getResult();
  }

  @Override
  public int getVersion() {
    WordsScanner scanner = createScanner();
    return scanner instanceof VersionedWordsScanner ? ((VersionedWordsScanner)scanner).getVersion() : -1;
  }
}
