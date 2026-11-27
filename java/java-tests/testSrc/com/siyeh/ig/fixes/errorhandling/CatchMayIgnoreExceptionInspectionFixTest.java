/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.errorhandling;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.errorhandling.CatchMayIgnoreExceptionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class CatchMayIgnoreExceptionInspectionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    CatchMayIgnoreExceptionInspection inspection = new CatchMayIgnoreExceptionInspection();
    inspection.m_ignoreCatchBlocksWithComments = false;
    return List.of(inspection);
  }, "errorhandling/ignore_exception");

  @Test
  public void testEmptyCatch() {
    ig.get().doTest(InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body"));
  }

  @Test
  public void testCatchWithComment() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body"));
  }

  @Test
  public void testRenameToIgnored() {
    ig.get().doTest("Rename 'ex' to 'ignored'");
  }

  @Test
  public void testRenameToIgnoredFixAll() {
    ig.get().doTest("Fix all 'Catch block may ignore exception' problems in file");
  }

  @Test
  public void testRenameToIgnoredJava21() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> ig.get().doTest("Rename 'ex' to '_'"));
  }

  @Test
  public void testRenameToIgnoredNameConflict() {
    ig.get().doTest("Rename 'ex' to 'ignored1'");
  }
}
