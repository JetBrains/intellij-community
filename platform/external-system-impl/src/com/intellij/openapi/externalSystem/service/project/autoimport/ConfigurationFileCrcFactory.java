// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheUtil;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.zip.CRC32;

/**
 * @author Vladislav.Soroka
 */
public class ConfigurationFileCrcFactory {
  private final Project myProject;
  private final VirtualFile myFile;

  public ConfigurationFileCrcFactory(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
  }

  public long create() {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
    if (psiFile != null) {
      CRC32 crc32 = createCrcUsingLexer(psiFile);
      if (crc32 == null) {
        crc32 = createCrcUsingWordsScanner(psiFile);
      }
      if (crc32 == null) {
        crc32 = createCrcUsingPsi(psiFile);
      }
      return crc32.getValue();
    }
    else {
      return myFile.getModificationStamp();
    }
  }

  @Nullable
  private CRC32 createCrcUsingLexer(@NotNull PsiFile psiFile) {
    Lexer lexer;
    TokenSet ignoredTokens;
    if (psiFile instanceof PsiFileBase) {
      ParserDefinition parserDefinition = ((PsiFileBase)psiFile).getParserDefinition();
      lexer = parserDefinition.createLexer(myProject);
      ignoredTokens = TokenSet.andSet(parserDefinition.getCommentTokens(), parserDefinition.getWhitespaceTokens());
    }
    else {
      final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(psiFile.getFileType(), myProject, myFile);
      if (syntaxHighlighter == null) return null;
      lexer = syntaxHighlighter.getHighlightingLexer();
      ignoredTokens = TokenSet.WHITE_SPACE;
    }

    CRC32 crc32 = new CRC32();
    lexer.start(psiFile.getText());
    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      if (!ignoredTokens.contains(tokenType) && !CacheUtil.isInComments(tokenType)) {
        String tokenText = lexer.getTokenText();
        if (!CharArrayUtil.containsOnlyWhiteSpaces(tokenText)) {
          for (int i = 0, end = tokenText.length(); i < end; i++) {
            crc32.update(tokenText.charAt(i));
          }
        }
      }
      lexer.advance();
    }
    return crc32;
  }

  @Nullable
  private CRC32 createCrcUsingWordsScanner(@NotNull PsiFile psiFile) {
    WordsScanner wordsScanner = getScanner(myFile);
    if (wordsScanner == null) return null;

    CRC32 crc32 = new CRC32();
    wordsScanner.processWords(psiFile.getText(), occurrence -> {
      if (occurrence.getKind() != WordOccurrence.Kind.COMMENTS) {
        CharSequence currentWord = occurrence.getBaseText().subSequence(occurrence.getStart(), occurrence.getEnd());
        for (int i = 0, end = currentWord.length(); i < end; i++) {
          crc32.update(currentWord.charAt(i));
        }
      }
      return true;
    });
    return crc32;
  }

  @Nullable
  private static WordsScanner getScanner(VirtualFile file) {
    FileType fileType = file.getFileType();
    final WordsScanner customWordsScanner = CacheBuilderRegistry.getInstance().getCacheBuilder(fileType);
    if (customWordsScanner != null) {
      return customWordsScanner;
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final FindUsagesProvider findUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      return findUsagesProvider == null ? null : findUsagesProvider.getWordsScanner();
    }
    return null;
  }

  private static CRC32 createCrcUsingPsi(@NotNull PsiFile psiFile) {
    CRC32 crc32 = new CRC32();
    ApplicationManager.getApplication().runReadAction(() -> psiFile.acceptChildren(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof LeafElement && !(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
          String text = element.getText();
          if (!text.trim().isEmpty()) {
            for (int i = 0, end = text.length(); i < end; i++) {
              crc32.update(text.charAt(i));
            }
          }
        }
        super.visitElement(element);
      }
    }));
    return crc32;
  }
}
