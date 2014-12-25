/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;

public class JavadocResolveTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc/resolve";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JavaDocLocalInspection(), new JavaDocReferenceInspection()};
  }

  public void testSee0() throws Exception { doTest(); }
  public void testSee1() throws Exception { doTest(); }
  public void testSee2() throws Exception { doTest(); }
  public void testSee3() throws Exception { doTest(); }
  public void testPackageInfo() throws Exception { doTest(BASE_PATH + "/pkg/package-info.java", BASE_PATH, false, false); }
  public void testBrokenPackageInfo() throws Exception { doTest(BASE_PATH + "/pkg1/package-info.java", BASE_PATH, false, false); }

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/pkg/" + getTestName(false) + ".java", BASE_PATH, false, false);
  }
}
