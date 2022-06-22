// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavadocLinkAsPlainTextInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavadocLinkAsPlainTextInspectionTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/inspection/javadocLinkAsPlainText/";


  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JavadocLinkAsPlainTextInspection()};
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }

  public void testLeadingAsterisks() {
    doTest();
  }

  public void testNoLeadingAsterisks() {
    doTest();
  }
}
