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

import com.intellij.idea.Bombed;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Calendar;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/ant";

  public void testDummy() {}

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".xml", false, false);
  }

  @Bombed(year = 2006, month = Calendar.JULY, day = 20, user = "lvo", time = 13, description = "Using entity")
  public void testEntity() throws Exception {
    configureByFiles(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".ent")
      },
      null
    );
    doDoTest(true, false);
  }

  @Bombed(year = 2006, month = Calendar.JULY, day = 20, user = "lvo", time = 13, description = "Duplicate targets & invalid text")
  public void testSanity() throws Exception { doTest(); }
}