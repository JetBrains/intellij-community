/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;

/**
 * This class intended for "heavily-loaded" tests only, e.g. those need to setup separate project directory structure to run
 * For "lightweight" tests use LightAdvHighlightingTest
 */
public class AdvHighlightingJdk7Test extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7/";

  @Override
  protected Sdk getTestProjectJdk() {
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_7);
    return JavaSdkImpl.getMockJdk17("java 1.7");
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DefUseInspection()};
  }

  public void testStaticImports() throws Exception {
    doTest(BASE_PATH + "staticImports/staticImports.java", BASE_PATH + "/staticImports", false, false);
  }

  public void testEnumSyntheticMethods() throws Exception {
    doTest(BASE_PATH + getTestName(true) + ".java", false, false);
  }

  public void testStaticImportConflict() throws Exception {
    doTest(BASE_PATH + "staticImportConflict/Usage.java", BASE_PATH + "/staticImportConflict", false, false);
  }

  public void testStaticOnDemandImportConflict() throws Exception {
    doTest(BASE_PATH + "staticImportConflict/UsageOnDemand.java", BASE_PATH + "/staticImportConflict", false, false);
  }
}
