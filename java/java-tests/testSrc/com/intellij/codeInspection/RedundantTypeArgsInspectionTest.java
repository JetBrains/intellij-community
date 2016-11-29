/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 19-Apr-2010
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class RedundantTypeArgsInspectionTest extends LightDaemonAnalyzerTestCase {

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] { new RedundantTypeArgsInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Throwable {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), getTestRootDisposable());
    doTest("/inspection/redundantTypeArgs/" + getTestName(false) + ".java", true, false);
  }

  public void testReturnPrimitiveTypes() throws Throwable { // javac non-boxing: IDEA-53984
    doTest();
  }

  public void testConditionalExpression() throws Throwable {
    doTest();
  }

  public void testBoundInference() throws Throwable {
    doTest();
  }

  public void testNestedCalls() throws Throwable {
    doTest();
  }
}