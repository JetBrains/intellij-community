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
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/ant";
  private boolean myIgnoreInfos;

  public void testDummy() {}

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".xml", false, false);
  }

  @Bombed(year = 2006, month = Calendar.AUGUST, day = 29, user = "lvo", time = 12, description = "Using entity")
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

  @Bombed(year = 2006, month = Calendar.AUGUST, day = 29, user = "lvo", time = 12, description = "Duplicate targets & invalid text")
  public void testSanity() throws Exception { doTest(); }

  public void testSanity2() throws Exception { doTest(); }

  public void testRefid() throws Exception { doTest(); }

  public void testExternalValidator() throws Exception { doTest(); }

  public void testProperties() throws Exception {
    configureByFiles(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".properties")
      },
      null
    );
    doDoTest(true, false);
  }

  @Bombed(year = 2006, month = Calendar.AUGUST, day = 29, user = "lvo", time = 12, description = "Task def from JAR")
  public void testProperties2() throws Exception {
    configureByFiles(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
        getVirtualFile(BASE_PATH + "/" + "yguard.jar")
      },
      null
    );
    doDoTest(true, false);
  }

  public void testAntFileProperties() throws Exception {
    doTest();
  }

  @Bombed(year = 2006, month = Calendar.AUGUST, day = 29, user = "lvo", time = 12, description = "Performance test")
  public void testBigFile() throws Exception {
    configureByFiles(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
        getVirtualFile(BASE_PATH + "/" + "buildserver.xml"),
        getVirtualFile(BASE_PATH + "/" + "buildserver.properties")
      },
      null
    );

    try {
      myIgnoreInfos = true;
      IdeaTestUtil.assertTiming(
      "Should be quite performant !",
        1000,
        new Runnable() {
          public void run() {
            doDoTest(true, false);
          }
        }
      );
    }
    finally {
      myIgnoreInfos = false;
    }
  }


  protected Collection<HighlightInfo> doHighlighting() {
    final Collection<HighlightInfo> infos = super.doHighlighting();
    if (!myIgnoreInfos) {
      return infos;
    }
    return Collections.emptyList();
  }
}