// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Language;
import com.intellij.lang.cacheBuilder.*;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Contract;
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
      WordsScanner scanner = LanguageFindUsages.getWordsScanner(lang);
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

  @Contract(value = "_ -> new", pure = true)
  @NotNull
  public static IdIndexer createDefaultIndexer(@NotNull WordsScanner scanner) {
    return new ScanningIdIndexer() {
      @Override
      protected WordsScanner createScanner() {
        return scanner;
      }
    };
  }

  @Contract("_ -> new")
  @NotNull
  public static WordsScanner createCustomFileTypeScanner(@NotNull final SyntaxTable syntaxTable) {
    return new DefaultWordsScanner(new CustomFileTypeLexer(syntaxTable, true),
                                   TokenSet.create(CustomHighlighterTokenType.IDENTIFIER),
                                   TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                   CustomHighlighterTokenType.MULTI_LINE_COMMENT),
                                   TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));

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
