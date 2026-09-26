// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.lexer.XmlLexerKt;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.testFramework.LightPlatformTestCase;

/**
 * A merging lexer collapses a run of same-type tokens into one, so a large enough comment or character data run is
 * produced by a single {@code getTokenType()} call. Callers count tokens, which makes such a run invisible to them --
 * the merge loop has to check cancellation itself, or lexing a file with one huge run cannot be interrupted at all.
 */
public class MergingLexerCancellationTest extends LightPlatformTestCase {

  /**
   * A long comment. The flex lexer emits XML_COMMENT_CHARACTERS in small pieces and the merging adapter collapses the
   * whole run into one token, so this is ~2M merge iterations inside a single getTokenType() call.
   */
  private static String oneHugeComment() {
    return "<root><!-- " + "word ".repeat(400_000) + " --></root>";
  }

  private static void assertLexingIsCancelled(String what, Runnable lexAll) {
    ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    indicator.start();
    indicator.cancel();

    try {
      ProgressManager.getInstance().runProcess(lexAll, indicator);
      fail(what + ": lexing a single huge merged token should have been cancelled");
    }
    catch (ProcessCanceledException expected) {
      // the merge loop noticed the cancelled indicator instead of running to the end of the run
    }
  }

  /** {@link com.intellij.platform.syntax.util.lexer.MergingLexerAdapter}, reached through the syntax XML lexer. */
  public void testHugeMergedTokenIsInterruptible() {
    String text = oneHugeComment();
    assertLexingIsCancelled("syntax adapter", () -> {
      Lexer lexer = XmlLexerKt.createXmlLexer(false);
      lexer.start(text);
      while (lexer.getTokenType() != null) {
        lexer.advance();
      }
    });
  }

  /** {@link com.intellij.lexer.MergingLexerAdapter}, which the obsolete {@link XmlLexer} still extends. */
  public void testHugeMergedTokenIsInterruptibleInPsiLexer() {
    String text = oneHugeComment();
    assertLexingIsCancelled("psi adapter", () -> {
      XmlLexer lexer = new XmlLexer();
      lexer.start(text);
      while (lexer.getTokenType() != null) {
        lexer.advance();
      }
    });
  }

  public void testLexingIsNotDisturbedWhenNothingIsCancelled() {
    String text = oneHugeComment();
    Lexer lexer = XmlLexerKt.createXmlLexer(false);
    lexer.start(text);

    int tokens = 0;
    int longest = 0;
    while (lexer.getTokenType() != null) {
      tokens++;
      longest = Math.max(longest, lexer.getTokenEnd() - lexer.getTokenStart());
      lexer.advance();
    }
    assertEquals(9, tokens);
    // the whole comment body arrived as one merged token, which is what makes the run uninterruptible without the check
    assertEquals(text.length() - 20, longest);
  }
}
