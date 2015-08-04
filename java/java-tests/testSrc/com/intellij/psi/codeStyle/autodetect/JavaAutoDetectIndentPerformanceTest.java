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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.JavaTestUtil;
import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class JavaAutoDetectIndentPerformanceTest extends AbstractIndentAutoDetectionTest {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/psi/autodetect/";
  }

  @NotNull
  @Override
  protected String getFileNameWithExtension() {
    return getTestName(true) + ".java";
  }

  public void testBigFile() {
    final CommonCodeStyleSettings.IndentOptions[] options = new CommonCodeStyleSettings.IndentOptions[1];

    PlatformTestUtil
      .startPerformanceTest("Detecting indent on newly opened file", 250, () -> options[0] = detectIndentOptions())
      .setup(() -> configureByFile(getFileNameWithExtension()))
      .cpuBound()
      .assertTiming();

    //to ensure if worked as expected
    Assert.assertEquals("Detect indent mistmatch", 2, options[0].INDENT_SIZE);

    PlatformTestUtil
      .startPerformanceTest("Detecting indent on hot file", 30, AbstractIndentAutoDetectionTest::detectIndentOptions)
      .cpuBound()
      .assertTiming();
  }
}