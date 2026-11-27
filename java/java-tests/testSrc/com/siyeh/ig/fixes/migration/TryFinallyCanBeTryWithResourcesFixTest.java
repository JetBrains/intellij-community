/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.migration;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.TryFinallyCanBeTryWithResourcesInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class TryFinallyCanBeTryWithResourcesFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new TryFinallyCanBeTryWithResourcesInspection()), "migration/try_finally_can_be_try_with_resources",
                        InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.quickfix"),
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_1_9));

  @Test
  public void testComments() { ig.get().doTest(); }
  @Test
  public void testMultiple() { ig.get().doTest(); }
  @Test
  public void testKeepResourceCallInBlock() { ig.get().doTest(); }
  @Test
  public void testTryInCatchBlock() { ig.get().doTest(); }
  @Test
  public void testJava9() { ig.get().doTest(); }
  @Test
  public void testJava9WithParameter() { ig.get().doTest(); }
  @Test
  public void testCatch() { ig.get().doTest(); }
  @Test
  public void testTryWithResource() { ig.get().doTest(); }
  @Test
  public void testFirstNotInitialized() { ig.get().doTest(); }
  @Test
  public void testIDEA209231() { ig.get().doTest(); }
  @Test
  public void testIDEA243497() { ig.get().doTest(); }
  @Test
  public void testUseCloseAfterTry() { ig.get().doTest(); }
  @Test
  public void testUseCloseBeforeTry() { ig.get().doTest(); }
  @Test
  public void testCatchInFinallyNoOuterCatchBlock() { ig.get().doTest(); }
  @Test
  public void testCatchInFinallyWithOuterCatchBlock() { ig.get().doTest(); }
  @Test
  public void testCatchWithSuffixInFinallyNoOuterCatchBlock() { ig.get().doTest(); }
  @Test
  public void testCatchWithSuffixInFinallyWithOuterCatchBlock() { ig.get().doTest(); }
  @Test
  public void testCatchInFinallyPreserveComments() { ig.get().doTest(); }
  @Test
  public void testDisjointCatchInFinally() { ig.get().doTest(); }
  @Test
  public void testDisjointCatchInOuterTry() { ig.get().doTest(); }
  @Test
  public void testSideEffectsFromOuterCatch() { ig.get().doTest();}
  @Test
  public void testSideEffectsFromInnerCatch() { ig.get().doTest();}

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("package test;" +
                       "public class MyBufferedInputStream implements java.io.Closeable {" +
                       "  public BufferedInputStream(InputStream in) {}" +
                       "}");
  }
}
