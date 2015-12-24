/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This class intended for "heavily-loaded" tests only, e.g. those need to setup separate project directory structure to run.
 * For "lightweight" tests please use {@linkplain LightAdvHighlightingJdk7Test}.
 */
public class AdvHighlightingJdk7Test extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7/";

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DefUseInspection()};
  }

  public void testStaticImports() throws Exception {
    doTest(BASE_PATH + "staticImports/staticImports.java", BASE_PATH + "/staticImports", false, false);
  }

  public void testStaticImportConflict() throws Exception {
    doTest(BASE_PATH + "staticImportConflict/Usage.java", BASE_PATH + "/staticImportConflict", false, false);
  }

  public void testStaticOnDemandImportConflict() throws Exception {
    doTest(BASE_PATH + "staticImportConflict/UsageOnDemand.java", BASE_PATH + "/staticImportConflict", false, false);
  }

  public void testStaticImportMethodShadowing() throws Exception {
    doTest(BASE_PATH + "staticImports/P1/MethodShadowing.java", BASE_PATH + "/staticImports", false, false);
  }

  public void testStaticAndSingleImportConflict() throws Exception {
    doTest(BASE_PATH + "staticImportConflict/UsageMixed.java", BASE_PATH + "/staticImportConflict", false, false);
  }

  public void testRawInnerClassImport() throws Exception {
    doTest(BASE_PATH + "raw/p/Class1.java", BASE_PATH + "/raw", false, false);
  }
  
  public void testRawInnerClassImportOnDemand() throws Exception {
    doTest(BASE_PATH + "rawOnDemand/p/Class1.java", BASE_PATH + "/rawOnDemand", false, false);
  }

  //ambiguous method calls
  private void doTestAmbiguous() throws Exception {
    doTestAmbiguous(JavaSdkVersion.JDK_1_7);
  }

  //ambiguous method calls
  private void doTestAmbiguous(@NotNull JavaSdkVersion javaSdkVersion) throws Exception {
    final String name = getTestName(true);
    IdeaTestUtil.setTestVersion(javaSdkVersion, getModule(), myTestRootDisposable);
    doTest(BASE_PATH + name + "/pck/AmbiguousMethodCall.java", BASE_PATH + "/" + name, false, false);
  }

  public void testAmbiguous() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousArrayInSubst() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousTypeParamExtends() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousTypeParamNmb() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousTypeParamNmb1() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousInheritance() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousInheritance1() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousVarargs() throws Exception {
    doTestAmbiguous(JavaSdkVersion.JDK_1_8);
  }

  public void testAmbiguousVarargs1() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMultiIntInheritance() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMultipleTypeParamExtends() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMultipleTypeParamExtends1() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMultipleTypeParamExtends2() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMultipleTypeParamExtends3() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57317() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57278() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57269() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67573() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57306() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67841() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57535() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67832() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67837() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA78027() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA25097() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA24768() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA21660() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA22547() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousInferenceOrder() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA87672() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57500() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67864() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67836() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67576() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA67519() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57569() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousMethodsFromSameClassAccess() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousIDEA57633() throws Exception {
    doTestAmbiguous();
  }

  public void testAmbiguousStaticImportMethod() throws Exception {
    doTestAmbiguous();
  }
}
