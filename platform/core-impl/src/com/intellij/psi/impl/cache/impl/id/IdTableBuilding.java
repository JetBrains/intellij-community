/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


public abstract class IdTableBuilding {
  private final HashMap<FileType, FileTypeIdIndexer> myIdIndexers = new HashMap<FileType, FileTypeIdIndexer>();

  public IdTableBuilding() {
    registerStandardIndexes();
  }

  protected abstract void registerStandardIndexes();

  private static class IdTableBuildingHolder {
    private static final IdTableBuilding ourInstance = ApplicationManager.getApplication().getComponent(IdTableBuilding.class);
  }

  public static IdTableBuilding getInstance() {
    return IdTableBuildingHolder.ourInstance;
  }

  public interface ScanWordProcessor {
    void run(CharSequence chars, @Nullable char[] charsArray, int start, int end);
  }

  public static class PlainTextIndexer extends FileTypeIdIndexer {
    @Override
    @NotNull
    public Map<IdIndexEntry, Integer> map(final FileContent inputData) {
      final IdDataConsumer consumer = new IdDataConsumer();
      final CharSequence chars = inputData.getContentAsText();
      scanWords(new ScanWordProcessor() {
        @Override
        public void run(final CharSequence chars11, @Nullable char[] charsArray, final int start, final int end) {
          if (charsArray != null) {
            consumer.addOccurrence(charsArray, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
          } else {
            consumer.addOccurrence(chars11, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
          }
        }
      }, chars, 0, chars.length());
      return consumer.getResult();
    }
  }

  @Deprecated
  public void registerIdIndexer(FileType fileType, FileTypeIdIndexer indexer) {
    myIdIndexers.put(fileType, indexer);
  }

  public boolean isIdIndexerRegistered(FileType fileType) {
    return myIdIndexers.containsKey(fileType) || IdIndexers.INSTANCE.forFileType(fileType) != null;
  }

  @Nullable
  public FileTypeIdIndexer getFileTypeIndexer(FileType fileType) {
    final FileTypeIdIndexer idIndexer = myIdIndexers.get(fileType);

    if (idIndexer != null) {
      return idIndexer;
    }

    final FileTypeIdIndexer extIndexer = IdIndexers.INSTANCE.forFileType(fileType);
    if (extIndexer != null) {
      return extIndexer;
    }

    final WordsScanner customWordsScanner = CacheBuilderRegistry.getInstance().getCacheBuilder(fileType);
    if (customWordsScanner != null) {
      return new WordsScannerFileTypeIdIndexerAdapter(customWordsScanner);
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final FindUsagesProvider findUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      WordsScanner scanner = findUsagesProvider == null ? null : findUsagesProvider.getWordsScanner();
      if (scanner == null) {
        scanner = new SimpleWordsScanner();
      }
      return new WordsScannerFileTypeIdIndexerAdapter(scanner);
    }

    return createFileTypeIdIndexer(fileType);
  }

  protected abstract FileTypeIdIndexer createFileTypeIdIndexer(FileType fileType);

  protected class WordsScannerFileTypeIdIndexerAdapter extends FileTypeIdIndexer {
    private final WordsScanner myScanner;

    public WordsScannerFileTypeIdIndexerAdapter(@NotNull final WordsScanner scanner) {
      myScanner = scanner;
    }

    @Override
    @NotNull
    public Map<IdIndexEntry, Integer> map(final FileContent inputData) {
      final CharSequence chars = inputData.getContentAsText();
      final char[] charsArray = CharArrayUtil.fromSequenceWithoutCopying(chars);
      final IdDataConsumer consumer = new IdDataConsumer();
      myScanner.processWords(chars, new Processor<WordOccurrence>() {
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
          if (kind == null) return UsageSearchContext.ANY;
          if (kind == WordOccurrence.Kind.CODE) return UsageSearchContext.IN_CODE;
          if (kind == WordOccurrence.Kind.COMMENTS) return UsageSearchContext.IN_COMMENTS;
          if (kind == WordOccurrence.Kind.LITERALS) return UsageSearchContext.IN_STRINGS;
          if (kind == WordOccurrence.Kind.FOREIGN_LANGUAGE) return UsageSearchContext.IN_FOREIGN_LANGUAGES;
          return 0;
        }
      });
      return consumer.getResult();
    }
  }

  public static void scanWords(final ScanWordProcessor processor, final CharSequence chars, final int startOffset, final int endOffset) {
    scanWords(processor, chars, CharArrayUtil.fromSequenceWithoutCopying(chars), startOffset, endOffset, false);
  }

  public static void scanWords(final ScanWordProcessor processor,
                               final CharSequence chars,
                               @Nullable final char[] charArray,
                               final int startOffset,
                               final int endOffset,
                               final boolean mayHaveEscapes) {
    int index = startOffset;
    final boolean hasArray = charArray != null;

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index >= endOffset) break ScanWordsLoop;
        final char c = hasArray ? charArray[index] : chars.charAt(index);

        if ((c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ||
            (Character.isJavaIdentifierStart(c) && c != '$')) {
          break;
        }
        index++;
        if (mayHaveEscapes && c == '\\') index++; //the next symbol is for escaping
      }
      int index1 = index;
      while (true) {
        index++;
        if (index >= endOffset) break;
        final char c = hasArray ? charArray[index] : chars.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }
      if (index - index1 > 100) continue; // Strange limit but we should have some!

      processor.run(chars, charArray, index1, index);
    }
  }
}
