// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.BlockMarkerCommentsInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class BlockMarkerCommentsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private BlockMarkerCommentsInspection myInspection;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/blockMarkerComments/");
    myInspection = new BlockMarkerCommentsInspection();
    myFixture.enableInspections(myInspection);
  }

  @AfterEach
  void tearDown() {
    try {
      if (myInspection != null) {
        codeInsightFixture.get().disableInspections(myInspection);
      }
    }
    finally {
      myInspection = null;
    }
  }

  private void doTestInspection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  private void doTestQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testFileName = myFixture.getTestName(false);
    myFixture.enableInspections(new BlockMarkerCommentsInspection());
    myFixture.configureByFile(testFileName + ".java");
    final IntentionAction intentionAction = myFixture.findSingleIntention("Remove block marker comment");
    assertNotNull(intentionAction);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intentionAction);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(testFileName + "_after.java", true);
    });
  }

  @Test
  public void testMethod() {
    doTestInspection();
  }

  @Test
  public void testIf() {
    doTestInspection();
  }

  @Test
  public void testLoop() {
    doTestInspection();
  }

  @Test
  public void testTryCatch() {
    doTestInspection();
  }

  @Test
  public void testClass() {
    doTestInspection();
  }

  @Test
  public void testRemoveBlockMarker() {
    doTestQuickFix();
  }
}
