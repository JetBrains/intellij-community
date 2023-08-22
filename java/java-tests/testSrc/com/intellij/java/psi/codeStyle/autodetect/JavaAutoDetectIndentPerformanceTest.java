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
package com.intellij.java.psi.codeStyle.autodetect;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.util.TimeoutUtil;
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
    long fileLoadTime = TimeoutUtil.measureExecutionTime(() -> configureByFile(getFileNameWithExtension()));

    long detectingTime = TimeoutUtil.measureExecutionTime(() -> ref.set(detectIndentOptions(getVFile(), getEditor().getDocument())));
    double ratio = (double)detectingTime / fileLoadTime;
    if (ratio > 0.3) {
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
    detectIndentOptions(getVFile(), getEditor().getDocument());
    
    PlatformTestUtil
      .startPerformanceTest("Detecting indent on hot file", 180,
                            () -> detectIndentOptions(getVFile(), getEditor().getDocument()))
      .assertTiming();
  }
  
  public void testBigOneLineFile() {
    configureByFile("oneLine.json");
    long time = TimeoutUtil.measureExecutionTime(
      () -> detectIndentOptions(getVFile(), getEditor().getDocument()));
    assertTrue(time < 40);
  }
}