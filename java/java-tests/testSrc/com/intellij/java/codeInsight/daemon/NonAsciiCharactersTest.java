// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.NonAsciiCharactersInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;

@TestApplication
public class NonAsciiCharactersTest {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/nonAsciiCharacters";
  private final NonAsciiCharactersInspection myInspection = new NonAsciiCharactersInspection();
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture =
    daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath(), () -> new LocalInspectionTool[]{myInspection});

  private void doTest(String extension) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(BASE_PATH + "/" + fixture.getTestName(false) + extension, true, false);
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
  }

  @Test
  public void testNotAsciiJavaInVariousContexts() {
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
  @Test
  public void testNotAsciiJavaInAnyWord() {
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

  @Test
  public void testMixedLanguagesJavaInAnyWord() {
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

  @Test
  public void testMixedLanguagesXMLInAnyWord() {
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
  @Test
  public void testMixedLanguagesXMLInAnyWordExceptString() {
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

  @Test
  public void testMixedLanguagesJavaInVariousContexts() {
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

  @Test
  public void testGroovy() {
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

  @Test
  public void testMixedLanguagesPlainTextInAnyWord() {
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

  @Test
  public void testRegexNamedGroupWithNonAscii() {
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS = false;
    myInspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = false;
    myInspection.CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD = false;
    myInspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
    myInspection.CHECK_FOR_NOT_ASCII_COMMENT = false;
    myInspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = false;
    myInspection.CHECK_FOR_FILES_CONTAINING_BOM = false;
    doTest(".java");
  }
}
