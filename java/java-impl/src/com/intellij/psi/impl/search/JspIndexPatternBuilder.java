/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorHighlighterCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JspPsiUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author yole
 */
public class JspIndexPatternBuilder implements IndexPatternBuilder {
  @Override
  public Lexer getIndexingLexer(final PsiFile file) {
    if (JspPsiUtil.isInJspFile(file)) {
      EditorHighlighter highlighter = null;

      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      final EditorHighlighter cachedEditorHighlighter;
      boolean alreadyInitializedHighlighter = false;

      if ((cachedEditorHighlighter = EditorHighlighterCache.getEditorHighlighterForCachesBuilding(document)) != null &&
          PlatformIdTableBuilding.checkCanUseCachedEditorHighlighter(file.getText(), cachedEditorHighlighter)) {
        highlighter = cachedEditorHighlighter;
        alreadyInitializedHighlighter = true;
      }
      else {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file.getProject(), virtualFile);
        }
      }

      if (highlighter != null) {
        return new LexerEditorHighlighterLexer(highlighter, alreadyInitializedHighlighter);
      }
    }

    return null;
  }

  @Override
  public TokenSet getCommentTokenSet(final PsiFile file) {
    final JspFile jspFile = JspPsiUtil.getJspFile(file);
    TokenSet commentTokens = TokenSet.orSet(JavaIndexPatternBuilder.XML_COMMENT_BIT_SET, StdTokenSets.COMMENT_BIT_SET);
    final ParserDefinition parserDefinition =
      LanguageParserDefinitions.INSTANCE.forLanguage(jspFile.getViewProvider().getTemplateDataLanguage());
    if (parserDefinition != null) {
      commentTokens = TokenSet.orSet(commentTokens, parserDefinition.getCommentTokens());
    }
    return commentTokens;
  }

  @Override
  public int getCommentStartDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "<%--".length() : 0;
  }

  @Override
  public int getCommentEndDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "--%>".length() : 0;
  }
}
