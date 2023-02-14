// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.NonAsciiCharactersInspection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class NonAsciiCharactersTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/nonAsciiCharacters";
  private NonAsciiCharactersInspection myInspection = new NonAsciiCharactersInspection();

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{myInspection};
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  private void doTest(String extension) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + extension, true, false);
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testNotAsciiJavaInVariousContexts() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = false;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = true;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = true;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".java");
  }
  public void testNotAsciiJavaInAnyWord() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = false;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = true;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = true;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".java");
  }

  public void testMixedLanguagesJavaInAnyWord() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".java");
  }

  public void testMixedLanguagesXMLInAnyWord() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".xml");
  }
  public void testMixedLanguagesXMLInAnyWordExceptString() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = false;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".xml");
  }

  public void testMixedLanguagesJavaInVariousContexts() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".java");
  }

  public void testGroovy() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = true;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = true;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".groovy");
  }

  public void testMixedLanguagesPlainTextInAnyWord() throws Exception {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = true;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".txt");
  }
}
