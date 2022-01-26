// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MissingJavadocHighlightingTest extends LightJavaCodeInsightFixtureTestCase5 {

  private MissingJavadocInspection myInspection;

  @BeforeEach
  void setUp() {
    MissingJavadocInspection inspection = new MissingJavadocInspection();
    getFixture().enableInspections(inspection);
    myInspection = inspection;
  }

  @Override
  protected @NotNull String getRelativePath() {
    return super.getRelativePath() + "/codeInsight/daemonCodeAnalyzer/missingJavadoc/";
  }

  @Test void testAllDisabled(){
    doTest();
  }

  @Test void testMethodEnabled(){
    myInspection.methodOptions.isEnabled = true;
    doTest();
  }

  @Test void testPrivateMethodEnabled(){
    myInspection.methodOptions.isEnabled = true;
    myInspection.methodOptions.minimalVisibility = "private";
    doTest();
  }

  @Test void testMethodIgnored(){
    myInspection.methodOptions.isEnabled = true;
    myInspection.methodOptions.minimalVisibility = "private";
    myInspection.ignoreDeprecated = true;
    myInspection.ignoreAccessors = true;
    doTest();
  }

  @Test void testPublicMemberInPrivateClass(){
    myInspection.methodOptions.isEnabled = true;
    myInspection.methodOptions.minimalVisibility = "public";
    myInspection.fieldOptions.isEnabled = true;
    myInspection.fieldOptions.minimalVisibility = "public";
    doTest();
  }

  @Test void testClassEnabled(){
    myInspection.topLevelClassOptions.isEnabled = true;
    doTest();
  }

  @Test void testFieldEnabled(){
    myInspection.fieldOptions.isEnabled = true;
    doTest();
  }

  @Test void testPrivateFieldEnabled(){
    myInspection.fieldOptions.isEnabled = true;
    myInspection.fieldOptions.minimalVisibility = "private";
    doTest();
  }

  @Test void testInnerClassEnabled(){
    myInspection.innerClassOptions.isEnabled = true;
    doTest();
  }

  @Test void testProtectedInnerClassEnabled(){
    myInspection.innerClassOptions.isEnabled = true;
    myInspection.innerClassOptions.minimalVisibility = "protected";
    doTest();
  }

  @Test void testRequiredTags(){
    myInspection.topLevelClassOptions.isEnabled = true;
    myInspection.topLevelClassOptions.setTagRequired("author", true);
    myInspection.topLevelClassOptions.setTagRequired("param", true);
    myInspection.methodOptions.isEnabled = true;
    doTest();
  }

  @Test void testPackageDisabled(){
    getFixture().testHighlighting("/packageInfo/disabled/package-info.java");
  }

  @Test void testPackageEnabled(){
    myInspection.packageOptions.isEnabled = true;
    getFixture().testHighlighting("/packageInfo/enabled/package-info.java");
  }

  @Test void testPackageRequiredTags(){
    myInspection.packageOptions.isEnabled = true;
    myInspection.packageOptions.setTagRequired("author", true);
    getFixture().testHighlighting("/packageInfo/requiredTags/package-info.java");
  }

  @Test void testModuleDisabled(){
    getFixture().testHighlighting("/moduleInfo/disabled/module-info.java");
  }

  @Test void testModuleEnabled(){
    myInspection.moduleOptions.isEnabled = true;
    getFixture().testHighlighting("/moduleInfo/enabled/module-info.java");
  }

  @Test void testModuleRequiredTags(){
    myInspection.moduleOptions.isEnabled = true;
    myInspection.moduleOptions.setTagRequired("author", true);
    getFixture().testHighlighting("/moduleInfo/requiredTags/module-info.java");
  }

  private void doTest(){
    getFixture().testHighlighting(getTestName(false) + ".java");
  }
}
