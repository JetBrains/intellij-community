/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.idea.LoggerFactory;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.cacheBuilder.*;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdTableBuilding {
  private IdTableBuilding() {
  }

  public interface ScanWordProcessor {
    void run(CharSequence chars, int start, int end);
  }

  public static class PlainTextIndexer extends FileTypeIdIndexer {
    @NotNull
    public Map<IdIndexEntry, Integer> map(final FileContent inputData) {
      final IdDataConsumer consumer = new IdDataConsumer();
      final CharSequence chars = inputData.getContentAsText();
      scanWords(new ScanWordProcessor(){
        public void run(final CharSequence chars11, final int start, final int end) {
          consumer.addOccurrence(chars11, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
        }
      }, chars, 0, chars.length());
      return consumer.getResult();
    }
  }

  public static class PlainTextTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    @NotNull
    public Map<TodoIndexEntry, Integer> map(final FileContent inputData) {
      final CharSequence chars = inputData.getContentAsText();


      final IndexPattern[] indexPatterns = CacheUtil.getIndexPatterns();
      if (indexPatterns.length > 0) {
        final TodoOccurrenceConsumer occurrenceConsumer = new TodoOccurrenceConsumer();
        for (IndexPattern indexPattern : indexPatterns) {
          Pattern pattern = indexPattern.getPattern();
          if (pattern != null) {
            Matcher matcher = pattern.matcher(chars);
            while (matcher.find()) {
              if (matcher.start() != matcher.end()) {
                occurrenceConsumer.incTodoOccurrence(indexPattern);
              }
            }
          }
        }
        Map<TodoIndexEntry, Integer> map = new HashMap<TodoIndexEntry, Integer>();
        for (IndexPattern indexPattern : indexPatterns) {
          final int count = occurrenceConsumer.getOccurrenceCount(indexPattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }

  }

  private static final HashMap<FileType,FileTypeIdIndexer> ourIdIndexers = new HashMap<FileType, FileTypeIdIndexer>();
  private static final HashMap<FileType,DataIndexer<TodoIndexEntry, Integer, FileContent>> ourTodoIndexers = new HashMap<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>>();

  @Deprecated
  public static void registerIdIndexer(FileType fileType,FileTypeIdIndexer indexer) {
    ourIdIndexers.put(fileType, indexer);
  }

  @Deprecated
  public static void registerTodoIndexer(FileType fileType, DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
    ourTodoIndexers.put(fileType, indexer);
  }

  public static boolean isIdIndexerRegistered(FileType fileType) {
    return ourIdIndexers.containsKey(fileType) || IdIndexers.INSTANCE.forFileType(fileType) != null;
  }

  public static boolean isTodoIndexerRegistered(FileType fileType) {
    return ourIdIndexers.containsKey(fileType) || TodoIndexers.INSTANCE.forFileType(fileType) != null;
  }
  
  static {
    registerIdIndexer(FileTypes.PLAIN_TEXT,new PlainTextIndexer());
    registerTodoIndexer(FileTypes.PLAIN_TEXT,new PlainTextTodoIndexer());

    registerIdIndexer(StdFileTypes.IDEA_MODULE, null);
    registerIdIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    registerIdIndexer(StdFileTypes.IDEA_PROJECT, null);

    registerTodoIndexer(StdFileTypes.IDEA_MODULE, null);
    registerTodoIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    registerTodoIndexer(StdFileTypes.IDEA_PROJECT, null);
  }

  @Nullable
  public static FileTypeIdIndexer getFileTypeIndexer(FileType fileType) {
    final FileTypeIdIndexer idIndexer = ourIdIndexers.get(fileType);

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

    if (fileType instanceof AbstractFileType) {
      return new WordsScannerFileTypeIdIndexerAdapter(createWordScaner((AbstractFileType)fileType));
    }

    return null;
  }

  private static WordsScanner createWordScaner(final AbstractFileType abstractFileType) {
    return new DefaultWordsScanner(new CustomFileTypeLexer(abstractFileType.getSyntaxTable(), true),
                                   TokenSet.create(CustomHighlighterTokenType.IDENTIFIER),
                                   TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                   CustomHighlighterTokenType.MULTI_LINE_COMMENT),
                                   TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));

  }

  private static final TokenSet ABSTRACT_FILE_COMMENT_TOKENS = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
  @Nullable
  public static DataIndexer<TodoIndexEntry, Integer, FileContent> getTodoIndexer(FileType fileType, final VirtualFile virtualFile) {
    final DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = ourTodoIndexers.get(fileType);

    if (indexer != null) {
      return indexer;
    }

    final DataIndexer<TodoIndexEntry, Integer, FileContent> extIndexer = TodoIndexers.INSTANCE.forFileType(fileType);
    if (extIndexer != null) {
      return extIndexer;
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
      if (commentTokens != null) {
        return new TokenSetTodoIndexer(commentTokens, virtualFile);
      }
    }

    if (fileType instanceof AbstractFileType) {
      return new TokenSetTodoIndexer(ABSTRACT_FILE_COMMENT_TOKENS, virtualFile);
    }

    return null;
  }

  private static class WordsScannerFileTypeIdIndexerAdapter extends FileTypeIdIndexer {
    private final WordsScanner myScanner;

    public WordsScannerFileTypeIdIndexerAdapter(@NotNull final WordsScanner scanner) {
      myScanner = scanner;
    }
    
    @NotNull
    public Map<IdIndexEntry, Integer> map(final FileContent inputData) {
      final CharSequence chars = inputData.getContentAsText();
      final char[] charsArray = CharArrayUtil.fromSequenceWithoutCopying(chars);
      final IdDataConsumer consumer = new IdDataConsumer();
      myScanner.processWords(chars, new Processor<WordOccurrence>() {
        public boolean process(final WordOccurrence t) {
          if(charsArray != null && t.getBaseText() == chars) {
            consumer.addOccurrence(charsArray, t.getStart(),t.getEnd(),convertToMask(t.getKind()));
          } 
          else {
            consumer.addOccurrence(t.getBaseText(), t.getStart(),t.getEnd(),convertToMask(t.getKind()));
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
  
  private static class TokenSetTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent>{
    @NotNull private final TokenSet myCommentTokens;
    private final VirtualFile myFile;

    public TokenSetTodoIndexer(@NotNull final TokenSet commentTokens, @NotNull final VirtualFile file) {
      myCommentTokens = commentTokens;
      myFile = file;
    }

    @NotNull
    public Map<TodoIndexEntry,Integer> map(final FileContent inputData) {
      if (CacheUtil.getIndexPatternCount() > 0) {
        final CharSequence chars = inputData.getContentAsText();
        final TodoOccurrenceConsumer occurrenceConsumer = new TodoOccurrenceConsumer();
        EditorHighlighter highlighter;

        final EditorHighlighter editorHighlighter = inputData.getUserData(FileBasedIndex.EDITOR_HIGHLIGHTER);
        if (editorHighlighter != null && checkCanUseCachedEditorHighlighter(chars, editorHighlighter)) {
          highlighter = editorHighlighter;
        } else {
          highlighter = HighlighterFactory.createHighlighter(null, myFile);
          highlighter.setText(chars);
        }

        final int documentLength = chars.length();
        BaseFilterLexer.TodoScanningData[] todoScanningDatas = null;
        final HighlighterIterator iterator = highlighter.createIterator(0);

        while (!iterator.atEnd()) {
          final IElementType token = iterator.getTokenType();

          if (myCommentTokens.contains(token) || CacheUtil.isInComments(token)) {
            int start = iterator.getStart();
            if (start >= documentLength) break;            
            int end = iterator.getEnd();

            todoScanningDatas = BaseFilterLexer.advanceTodoItemsCount(
              chars.subSequence(start, Math.min(end, documentLength)), 
              occurrenceConsumer, 
              todoScanningDatas
            );
            if (end > documentLength) break;
          }
          iterator.advance();
        }
        final Map<TodoIndexEntry,Integer> map = new HashMap<TodoIndexEntry, Integer>();
        for (IndexPattern pattern : CacheUtil.getIndexPatterns()) {
          final int count = occurrenceConsumer.getOccurrenceCount(pattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(pattern.getPatternString(), pattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }
  }

  public static boolean checkCanUseCachedEditorHighlighter(final CharSequence chars, final EditorHighlighter editorHighlighter) {
    assert editorHighlighter instanceof LexerEditorHighlighter;
    final boolean b = ((LexerEditorHighlighter)editorHighlighter).checkContentIsEqualTo(chars);
    if (!b) {
      final Logger logger = LoggerFactory.getInstance().getLoggerInstance(IdTableBuilding.class.getName());
      logger.warn("Unexpected mismatch of editor highlighter content with indexing content");
    }
    return b;
  }

  public static void scanWords(final ScanWordProcessor processor, final CharSequence chars, final int startOffset, final int endOffset) {
    scanWords(processor, chars, CharArrayUtil.fromSequenceWithoutCopying(chars), startOffset, endOffset, false);
  }

  public static void scanWords(final ScanWordProcessor processor, final CharSequence chars, @Nullable final char[] charArray, final int startOffset,
                               final int endOffset,
                               final boolean mayHaveEscapes) {
    int index = startOffset;
    final boolean hasArray = charArray != null;

    ScanWordsLoop:
      while(true){
        while(true){
          if (index >= endOffset) break ScanWordsLoop;
          final char c = hasArray ? charArray[index]:chars.charAt(index);

          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (Character.isJavaIdentifierStart(c) && c != '$')) break;
          index++;
          if (mayHaveEscapes && c == '\\') index++; //the next symbol is for escaping
        }
        int index1 = index;
        while(true){
          index++;
          if (index >= endOffset) break;
          final char c = hasArray ? charArray[index]:chars.charAt(index);
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
          if (!Character.isJavaIdentifierPart(c) || c == '$') break;
        }
        if (index - index1 > 100) continue; // Strange limit but we should have some!

        processor.run(chars, index1, index);
      }
  }

}
