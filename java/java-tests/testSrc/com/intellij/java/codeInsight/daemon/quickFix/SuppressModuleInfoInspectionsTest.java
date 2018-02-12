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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class SuppressModuleInfoInspectionsTest extends LightCodeInsightFixtureTestCase {

  public void testModuleInfo() {
    // need to set jdk version here because it is not set correctly in the mock jdk which causes problems in
    // com.intellij.codeInspection.JavaSuppressionUtil#canHave15Suppressions()
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), getTestRootDisposable());
    final LocalInspectionTool inspection = new JavaDocReferenceInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("module-info.java");
    myFixture.testHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for module declaration"));
    myFixture.checkResultByFile("module-info.after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/suppressModuleInfoInspections";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}