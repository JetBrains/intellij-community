/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.java.codeInsight;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@TestApplication
public class ClsGenerics15HighlightingTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    ClsGenericsHighlightingTestUtil.clsGenericsHighlightingFixture(LanguageLevel.JDK_1_5);
  private String myTestName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    myTestName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  private void doTest() {
    ClsGenericsHighlightingTestUtil.doTest(codeInsightFixture.get(), myTestName);
  }

  @Test
  public void testIDEA97887() { doTest(); }
  @Test
  public void testIDEA118733() { doTest(); }
  @Test
  public void testIDEA146452() { doTest(); }
}
