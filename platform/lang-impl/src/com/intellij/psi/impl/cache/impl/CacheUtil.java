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

package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.caches.FileContent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.io.IOException;

public class CacheUtil {
  public static final Key<Boolean> CACHE_COPY_KEY = new Key<Boolean>("CACHE_COPY_KEY");

  private CacheUtil() {
  }

  public static PsiFile createFileCopy(PsiFile psiFile) {
    return createFileCopy(null, psiFile);
  }

  public static boolean isCopy(PsiFile psiFile) {
    return psiFile.getUserData(CACHE_COPY_KEY) != null;
  }

  public static PsiFile createFileCopy(FileContent content, PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return psiFile; // It's already a copy created via PsiManager.getFile(FileContent). Usually happens on initial startup.

    if (psiFile instanceof PsiFileEx) {
      return ((PsiFileEx)psiFile).cacheCopy(content);
    }

    return psiFile;
  }

  private static final Key<CharSequence> CONTENT_KEY = new Key<CharSequence>("CONTENT_KEY");

  public static CharSequence getContentText(final FileContent content) {
    final Document doc = FileDocumentManager.getInstance().getCachedDocument(content.getVirtualFile());
    if (doc != null) return doc.getCharsSequence();

    CharSequence cached = content.getUserData(CONTENT_KEY);
    if (cached != null) return cached;
    try {
      cached = LoadTextUtil.getTextByBinaryPresentation(content.getBytes(), content.getVirtualFile(), false);
      cached = content.putUserDataIfAbsent(CONTENT_KEY, cached);
      return cached;
    }
    catch (IOException e) {
      return "";
    }
  }

  public static IndexPatternProvider[] getIndexPatternProviders() {
    return Extensions.getExtensions(IndexPatternProvider.EP_NAME);
  }

  public static int getIndexPatternCount() {
    int patternsCount = 0;
    for(IndexPatternProvider provider: getIndexPatternProviders()) {
      patternsCount += provider.getIndexPatterns().length;
    }
    return patternsCount;
  }

  public static IndexPattern[] getIndexPatterns() {
    IndexPattern[] result = new IndexPattern[getIndexPatternCount()];
    int destIndex = 0;
    for(IndexPatternProvider provider: getIndexPatternProviders()) {
      for(IndexPattern pattern: provider.getIndexPatterns()) {
        result [destIndex++] = pattern;        
      }
    }
    return result;
  }

  public static boolean isInComments(final IElementType tokenType) {
    final Language language = tokenType.getLanguage();
    boolean inComments = false;

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

    if (parserDefinition != null) {
      final TokenSet commentTokens = parserDefinition.getCommentTokens();

      if (commentTokens.contains(tokenType)) {
        inComments = true;
      }
    }
    return inComments;
  }
}
