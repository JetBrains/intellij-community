// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.NonAsciiCharactersInspection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class NonAsciiCharactersTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/nonAsciiCharacters";

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    NonAsciiCharactersInspection inspection = new NonAsciiCharactersInspection();
    inspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
    inspection.CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING = true;
    inspection.CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
    inspection.CHECK_FOR_NOT_ASCII_COMMENT = true;
    inspection.CHECK_FOR_NOT_ASCII_STRING_LITERAL = true;
    inspection.CHECK_FOR_FILES_CONTAINING_BOM = true;
    return new LocalInspectionTool[]{inspection};
  }

  private void doTest(String extension) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + extension, true, false);
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testSimple() throws Exception {
    doTest(".java");
  }
  public void testGroovy() throws Exception {
    doTest(".groovy");
  }
}
