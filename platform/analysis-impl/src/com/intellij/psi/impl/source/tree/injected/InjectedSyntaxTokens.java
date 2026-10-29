// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer tokens of an injected file, mapped back to its injection hosts, for the injected syntax highlighting.
 * Computed on demand from the current shreds and cached per injected file until the next PSI change.
 */
final class InjectedSyntaxTokens {
  private static final Key<ParameterizedCachedValue<List<TokenInfo>, Input>> KEY = Key.create("INJECTED_SYNTAX_TOKENS");
  // the scheme comes in as a parameter, so the cached value never keeps the scheme (or the editor) of its first caller
  private static final ParameterizedCachedValueProvider<List<TokenInfo>, Input> PROVIDER = input ->
    Result.create(List.copyOf(compute(input.injectedPsi(), input.scheme())), PsiModificationTracker.MODIFICATION_COUNT, input.injectedPsi());

  record TokenInfo(@NotNull IElementType type,
                   @NotNull ProperTextRange rangeInsideInjectionHost,
                   int shredIndex,
                   @NotNull TextAttributesKey @NotNull [] textAttributesKeys) {
  }

  private record Input(@NotNull PsiFile injectedPsi, @NotNull EditorColorsScheme scheme) {
  }

  private InjectedSyntaxTokens() {
  }

  @RequiresReadLock
  static @NotNull @Unmodifiable List<TokenInfo> get(@NotNull PsiFile injectedPsi, @NotNull EditorColorsScheme scheme) {
    // only getTextAttributesKeys() is harvested, which is scheme-independent, so the scheme is not part of the cache key
    return CachedValuesManager.getManager(injectedPsi.getProject())
      .getParameterizedCachedValue(injectedPsi, KEY, PROVIDER, false, new Input(injectedPsi, scheme));
  }

  // returns lexer element types with corresponding ranges in encoded (injection host based) PSI
  private static @NotNull List<TokenInfo> compute(@NotNull PsiFile injectedPsi, @NotNull EditorColorsScheme scheme) {
    Place shreds = InjectedLanguageUtilBase.getShreds(injectedPsi);
    if (shreds == null) return List.of();
    int shredCount = shreds.size();
    LiteralTextEscaper<?>[] escapers = new LiteralTextEscaper<?>[shredCount];
    TextRange[] rangesInsideHost = new TextRange[shredCount];
    TextRange[] rangesInDecoded = new TextRange[shredCount];
    StringBuilder outChars = new StringBuilder();
    for (int i = 0; i < shredCount; i++) {
      PsiLanguageInjectionHost.Shred shred = shreds.get(i);
      PsiLanguageInjectionHost host = shred.getHost();
      if (host == null) return List.of();
      LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = host.createLiteralTextEscaper();
      TextRange rangeInsideHost = shred.getRangeInsideHost();
      int start = outChars.length();
      outChars.append(shred.getPrefix());
      // decode() also prepares the escaper for getOffsetInHost() below
      if (!rangeInsideHost.isEmpty()) escaper.decode(rangeInsideHost, outChars);
      outChars.append(shred.getSuffix());
      escapers[i] = escaper;
      rangesInsideHost[i] = rangeInsideHost;
      rangesInDecoded[i] = new ProperTextRange(start, outChars.length());
    }

    FileViewProvider viewProvider = injectedPsi.getViewProvider();
    VirtualFile file = viewProvider.getVirtualFile();
    Language language = injectedPsi.getLanguage();
    Project project = injectedPsi.getProject();
    LanguageFileType fileType = language.getAssociatedFileType();
    var provider = fileType != null && fileType.getLanguage() == language
                   ? FileTypeEditorHighlighterProviders.getInstance().forFileType(fileType) : null;
    EditorHighlighter highlighter =
      provider != null
      ? provider.getEditorHighlighter(project, fileType, file, scheme)
      : new LexerEditorHighlighter(SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file), scheme);
    highlighter.setText(outChars);
    HighlighterIterator iterator = highlighter.createIterator(0);
    int hostNum = -1;
    int prevHostEndOffset = 0;
    LiteralTextEscaper<?> escaper = null;
    int prefixLength = 0;
    int suffixLength = 0;
    TextRange rangeInsideHost = null;
    int shredEndOffset = -1;
    List<TokenInfo> tokens = new ArrayList<>(outChars.length() / 5); // avg. token per 5 chars
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      TextRange range = new ProperTextRange(iterator.getStart(), iterator.getEnd());
      while (range != null && !range.isEmpty()) {
        if (range.getStartOffset() >= shredEndOffset) {
          hostNum++;
          PsiLanguageInjectionHost.Shred shred = shreds.get(hostNum);
          shredEndOffset = rangesInDecoded[hostNum].getEndOffset();
          prevHostEndOffset = range.getStartOffset();
          escaper = escapers[hostNum];
          rangeInsideHost = rangesInsideHost[hostNum];
          prefixLength = shred.getPrefix().length();
          suffixLength = shred.getSuffix().length();
        }
        //in prefix/suffix or spills over to next fragment
        if (range.getStartOffset() < prevHostEndOffset + prefixLength) {
          range = new UnfairTextRange(prevHostEndOffset + prefixLength, range.getEndOffset());
        }
        TextRange spilled = null;
        if (range.getEndOffset() > shredEndOffset - suffixLength) {
          spilled = new UnfairTextRange(shredEndOffset, range.getEndOffset());
          range = new UnfairTextRange(range.getStartOffset(), shredEndOffset - suffixLength);
        }
        if (!range.isEmpty()) {
          int start = escaper.getOffsetInHost(range.getStartOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (start == -1) start = rangeInsideHost.getStartOffset();
          int end = escaper.getOffsetInHost(range.getEndOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (end == -1) {
            end = rangeInsideHost.getEndOffset();
            prevHostEndOffset = shredEndOffset;
          }
          ProperTextRange rangeInHost = new ProperTextRange(start, end);
          tokens.add(new TokenInfo(tokenType, rangeInHost, hostNum,
                                   iterator.getTextAttributesKeys()));
        }
        range = spilled;
      }
      iterator.advance();
    }
    return tokens;
  }
}
