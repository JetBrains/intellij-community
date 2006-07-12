/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 13, 2006
 * Time: 12:55:30 AM
 */
package com.intellij.codeInsight.daemon;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/ant";

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".xml", false, false);
  }

  public void testSanity() throws Exception { doTest(); }
}