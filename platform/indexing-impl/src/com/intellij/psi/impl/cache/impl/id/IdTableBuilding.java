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

import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Language;
import com.intellij.lang.cacheBuilder.*;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class IdTableBuilding {
  private IdTableBuilding() {
  }

  public interface ScanWordProcessor {
    void run(CharSequence chars, @Nullable char[] charsArray, int start, int end);
  }

  private static final Map<FileType, IdIndexer> ourIdIndexers = new HashMap<>();

  @Deprecated
  public static void registerIdIndexer(@NotNull FileType fileType, FileTypeIdIndexer indexer) {
    ourIdIndexers.put(fileType, indexer);
  }

  public static boolean isIdIndexerRegistered(@NotNull FileType fileType) {
    return ourIdIndexers.containsKey(fileType) || IdIndexers.INSTANCE.forFileType(fileType) != null || fileType instanceof InternalFileType;
  }


  @Nullable
  public static IdIndexer getFileTypeIndexer(FileType fileType) {
    final IdIndexer idIndexer = ourIdIndexers.get(fileType);

    if (idIndexer != null) {
      return idIndexer;
    }

    final IdIndexer extIndexer = IdIndexers.INSTANCE.forFileType(fileType);
    if (extIndexer != null) {
      return extIndexer;
    }

    final WordsScanner customWordsScanner = CacheBuilderRegistry.getInstance().getCacheBuilder(fileType);
    if (customWordsScanner != null) {
      return createDefaultIndexer(customWordsScanner);
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final FindUsagesProvider findUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      WordsScanner scanner = findUsagesProvider == null ? null : findUsagesProvider.getWordsScanner();
      if (scanner == null) {
        scanner = new SimpleWordsScanner();
      }
      return createDefaultIndexer(scanner);
    }

    if (fileType instanceof CustomSyntaxTableFileType) {
      return createDefaultIndexer(createCustomFileTypeScanner(((CustomSyntaxTableFileType)fileType).getSyntaxTable()));
    }

    return null;
  }

  @NotNull
  public static WordsScannerFileTypeIdIndexerAdapter createDefaultIndexer(WordsScanner customWordsScanner) {
    return new WordsScannerFileTypeIdIndexerAdapter(customWordsScanner);
  }

  public static WordsScanner createCustomFileTypeScanner(SyntaxTable syntaxTable) {
    return new DefaultWordsScanner(new CustomFileTypeLexer(syntaxTable, true),
                                   TokenSet.create(CustomHighlighterTokenType.IDENTIFIER),
                                   TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                   CustomHighlighterTokenType.MULTI_LINE_COMMENT),
                                   TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));

  }

  private static class WordsScannerFileTypeIdIndexerAdapter implements IdIndexer {
    private final WordsScanner myScanner;

    public WordsScannerFileTypeIdIndexerAdapter(@NotNull final WordsScanner scanner) {
      myScanner = scanner;
    }

    @Override
    @NotNull
    public Map<IdIndexEntry, Integer> map(@NotNull final FileContent inputData) {
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
      return myScanner instanceof VersionedWordsScanner ? ((VersionedWordsScanner)myScanner).getVersion() : -1;
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
