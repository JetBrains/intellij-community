/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.impl.DefaultFileTypeFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public abstract class LexerTestCase extends LiteFixture {
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtension(FileTypeFactory.FILE_TYPE_FACTORY_EP, new DefaultFileTypeFactory());
  }

  protected void doTest(@NonNls String text) {
    Lexer lexer = createLexer();
    lexer.start(text.toCharArray());
    String result = "";
    for (; ;) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        break;
      }
      String tokenText = getTokenText(lexer);
      String tokenTypeName = tokenType.toString();
      String line = tokenTypeName + " ('" + tokenText + "')\n";
      result += line;
      lexer.advance();
    }
    assertSameLinesWithFile(PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + ".txt", result);

  }

  private static String getTokenText(Lexer lexer) {
    String text = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    text = StringUtil.replace(text, "\n", "\\n");
    return text;
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();
}
