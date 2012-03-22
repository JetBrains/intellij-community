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

import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.idea.LoggerFactory;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.TokenSet;

public class IdTableBuildingComponent extends IdTableBuilding {

  @Override
  protected void registerStandardIndexes() {
    registerIdIndexer(FileTypes.PLAIN_TEXT, new PlainTextIndexer());

    registerIdIndexer(StdFileTypes.IDEA_MODULE, null);
    registerIdIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    registerIdIndexer(StdFileTypes.IDEA_PROJECT, null);
  }

  @Override
  protected FileTypeIdIndexer createFileTypeIdIndexer(FileType fileType) {
    if (fileType instanceof AbstractFileType) {
      return new WordsScannerFileTypeIdIndexerAdapter(createWordScanner((AbstractFileType)fileType));
    }

    return null;
  }

  protected WordsScanner createWordScanner(final AbstractFileType abstractFileType) {
    return new DefaultWordsScanner(new CustomFileTypeLexer(abstractFileType.getSyntaxTable(), true),
                                   TokenSet.create(CustomHighlighterTokenType.IDENTIFIER),
                                   TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                   CustomHighlighterTokenType.MULTI_LINE_COMMENT),
                                   TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));

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
}
