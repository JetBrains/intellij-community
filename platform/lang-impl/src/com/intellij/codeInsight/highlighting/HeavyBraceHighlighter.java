// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for background brace highlighting extension<p>
 * How to use: 1) implement this interface and register this implementation via {@code com.intellij.heavyBracesHighlighter} extension point. <p>
 * Example: <pre>{@code
 * class MyHeavyBraceHighlighter implements HeavyBraceHighlighter {
 *   Pair<TextRange, TextRange> matchBrace(PsiFile file, int offset) {
 *     // compute paired brace in background...
 *   }
 * }
 * }</pre>
 * 2) Then register in your plugin's {@code plugin.xml} like this:
 * <pre>{@code
 *   <extension defaultExtensionNs="com.intellij">
 *     <heavyBracesHighlighter implementation="com.my.MyHeavyBraceHighlighter"/>
 *   </extension>
 * }</pre> <p>
 * Note: use this interface for brace matchers which are too heavy/slow to execute in EDT.
 * For example, those relying on network calls to LSP to retrieve the necessary information.
 * Otherwise, please use {@link PairedBraceMatcher} for light-weight matchers needing only the syntax structure to work correctly.
 * E.g., {@link com.intellij.codeInsight.highlighting.JavaBraceMatcher JavaBraceMatcher}
 * matches {@link com.intellij.psi.JavaTokenType#LPARENTH JavaTokenType.LPARENTH}
 * to {@link com.intellij.psi.JavaTokenType#RPARENTH JavaTokenType.RPARENTH} and can do that quickly in EDT.
 *
 * @see com.intellij.codeInsight.highlighting.HeavyHighlighterSample HeavyHighlighterSample: Working example how to implement this interface
 * @see com.intellij.codeInsight.highlighting.JavaBraceMatcher
 */
public abstract class HeavyBraceHighlighter {
  public static final ExtensionPointName<HeavyBraceHighlighter> EP_NAME =
    ExtensionPointName.create("com.intellij.heavyBracesHighlighter");

  /**
   * Extension point's public interface. First, it filters out applicable extensions
   * via the {@link HeavyBraceHighlighter#isAvailable(PsiFile, int) isAvailable()} method.
   * Second, these extensions are polled in unspecified order and first non-null {@link TextRange TextRange} returned.
   * Null is returned if nothing matched or no available extensions ready.
   * This method is supposed to be called inside a background read action thread.
   *
   * @param file   {@link PsiFile PsiFile} to match in.
   * @param offset An offset in {@link PsiFile PsiFile} {@code file} to match in.
   * @return Nullable {@link Pair Pair} of {@link TextRange TextRange} result.
   */
  public static @Nullable Pair<TextRange, TextRange> match(@NotNull PsiFile file, int offset) {
    if (!file.isValid()) return null;

    for (HeavyBraceHighlighter highlighter : EP_NAME.getExtensionList()) {
      if (highlighter.isAvailable(file, offset)) {
        Pair<TextRange, TextRange> pair = highlighter.matchBrace(file, offset);
        if (pair != null) {
          return pair;
        }
      }
    }
    return null;
  }

  /**
   * Used as a fast check when possible to filter out applicable extensions.
   * This method is supposed to be called inside a background read action thread.
   *
   * @return false if there is no way of matching anything for the given file and offset, true otherwise
   * @see PsiFile
   */
  protected boolean isAvailable(@NotNull PsiFile file, int offset) {
    return true;
  }

  /**
   * Tries to match braces nearby the caret returning text ranges of braces to highlight on match.
   * This method is supposed to be called inside a background read action thread.
   *
   * @return null if nothing matched or some implementation-specific error occurred,
   * a {@link Pair Pair} of {@link TextRange TextRange} to highlight otherwise.
   * @see CaretModel
   */
  protected abstract @Nullable Pair<TextRange, TextRange> matchBrace(@NotNull PsiFile file, int offset);
}
