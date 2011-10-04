package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 10/4/11 1:28 PM
 */
public class StringLiteralCopyPasteProcessorTest {
  
  @Test
  public void escapePastedStringLiteral() {
    doEscapePastedStringLiteralTest("identity", "identity");
    doEscapePastedStringLiteralTest("\"complete \n literal\"", "\"complete \\n literal\"");
    doEscapePastedStringLiteralTest("\"incomplete \n literal", "\"incomplete \n literal");
    doEscapePastedStringLiteralTest("partial \"string \n literal\"", "partial \"string \\n literal\"");
  }

  private static void doEscapePastedStringLiteralTest(@NotNull String initial, @NotNull String expected) {
    assertEquals(expected, StringLiteralCopyPasteProcessor.escapePastedLiteral(initial));
  }
}
