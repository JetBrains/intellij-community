// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Interface for (background) brace highlighting <p>
 * To use it, implement this interface and register this implementation via com.intellij.heavyBracesHighlighter extension point. <p>
 * Example: <pre>{@code
 * class MyHeavyBraceHighlighter implements HeavyBraceHighlighter {
 *  Pair<TextRange, TextRange> matchBrace(PsiFile file, int offset) {
 *    return isOnMyBrace(offset) ? paieredBrace(offset) : null;
 *  }
 * }
 * }</pre>
 * It should be added via plugin.xml like this:
 * <pre>{@code
 *   <extension defaultExtensionNs="com.intellij">
 *     <heavyBracesHighlighter implementation="com.intellij.codeInsight.highlighting.HeavyHighlighterSample"/>
 *   </extension>
 * }</pre> <p>
 * Note: use this interface for brace matchers which are too heavy/slow to execute in EDT,
 *  for example those relying on network calls to LSP to retrieve the necessary information.
 *  Otherwise, please use PairedBraceMatcher for light-weight matchers needing only the syntax structure to work correctly.
 *  E.g. {@link com.intellij.codeInsight.highlighting.JavaBraceMatcher JavaBraceMatcher}
 *  matches {@link com.intellij.psi.JavaTokenType#LPARENTH JavaTokenType.LPARENTH}
 *  to {@link com.intellij.psi.JavaTokenType#RPARENTH JavaTokenType.RPARENTH} and can do that quickly in EDT.
 *
 * @see com.intellij.codeInsight.highlighting.HeavyBraceHighlighterTest
 * @see com.intellij.codeInsight.highlighting.HeavyHighlighterSample
 * @see com.intellij.codeInsight.highlighting.JavaBraceMatcher
 * @see BackgroundHighlighter#updateHighlighted(Project, Editor)
 * @see PairedBraceMatcherAdapter
 */
public abstract class HeavyBraceHighlighter {
  public final static ExtensionPointName<HeavyBraceHighlighter> EP_NAME =
    ExtensionPointName.create("com.intellij.heavyBracesHighlighter");

  /**
   * Extension point's public interface. First, it filters out applicable extensions
   * via the {@link HeavyBraceHighlighter#isAvailable(PsiFile, int) isAvailable()} method.
   * Second, these extensions are polled in unspecified order and first non-null {@link TextRange TextRange} returned.
   * Null is returned if nothing matched or no available extensions ready.
   *
   * @param file   {@link PsiFile PsiFile} to match in.
   * @param offset An offset in {@link PsiFile PsiFile} {@code file} to match in.
   * @return Nullable {@link Pair Pair} of {@link TextRange TextRange} result.
   */
  @Nullable
  public static Pair<TextRange, TextRange> match(@NotNull PsiFile file, int offset) {
    if (!file.isValid()) return null;

    return EP_NAME.getExtensionList().stream()
      .filter((ext) -> ext.isAvailable(file, offset))
      .map((ext) -> ext.matchBrace(file, offset))
      .filter(Objects::nonNull)
      .findAny()
      .orElse(null);
  }

  /**
   * Used as a fast check when possible to filter out applicable extensions
   *
   * @return false if there is no way of matching anything for the given file and offset, true otherwise
   * @see PsiFile
   */
  protected boolean isAvailable(@NotNull PsiFile file, int offset) {
    return true;
  }

  /**
   * Tries to match braces nearby the caret returning text ranges of braces to highlight on match.
   *
   * @return null if nothing matched or some implementation-specific error occurred,
   * a {@link Pair Pair} of {@link TextRange TextRange} to highlight otherwise.
   * @see CaretModel
   */
  @Nullable
  protected abstract Pair<TextRange, TextRange> matchBrace(@NotNull PsiFile file, int offset);
}
