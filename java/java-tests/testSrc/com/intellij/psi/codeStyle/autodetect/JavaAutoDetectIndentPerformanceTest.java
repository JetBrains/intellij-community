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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TeamCityLogger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public class JavaAutoDetectIndentPerformanceTest extends AbstractIndentAutoDetectionTest {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/psi/autodetect/";
  }

  @NotNull
  @Override
  protected String getFileNameWithExtension() {
    return "bigFile.java";
  }

  public void testBigColdFile() {
    Ref<IndentOptions> ref = Ref.create();
    long fileLoadTime = trackTime(() -> configureByFile(getFileNameWithExtension()));

    long detectingTime = trackTime(() -> ref.set(detectIndentOptions()));
    double ratio = (double)detectingTime / fileLoadTime;
    if (ratio > 0.2) {
      TeamCityLogger.error("Detecting indent have taken too much time proportionally to file read time " + ratio);
    } else {
      String msg = "Detecting indent relatively to file read " + ratio;
      TeamCityLogger.info(msg);
      System.out.println(msg);
    }
    
    //to ensure it worked as expected
    Assert.assertEquals("Detect indent mismatch", 2, ref.get().INDENT_SIZE);
  }
  
  public void testBigHotFile() {
    configureByFile(getFileNameWithExtension());
    AbstractIndentAutoDetectionTest.detectIndentOptions();
    
    PlatformTestUtil
      .startPerformanceTest("Detecting indent on hot file", 40, AbstractIndentAutoDetectionTest::detectIndentOptions)
      .cpuBound()
      .useLegacyScaling().assertTiming();
  }
  
  public void testBigOneLineFile() {
    configureByFile("oneLine.json");
    long time = trackTime(AbstractIndentAutoDetectionTest::detectIndentOptions);
    assertTrue(time < 40);
  }
  

  private static long trackTime(Runnable runnable) {
    long startTime = System.currentTimeMillis();
    runnable.run();
    return System.currentTimeMillis() - startTime;
  }
}