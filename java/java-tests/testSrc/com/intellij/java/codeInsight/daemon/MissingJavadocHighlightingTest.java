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
    inspection.METHOD_SETTINGS.ENABLED = false;
    inspection.MODULE_SETTINGS.ENABLED = false;
    inspection.FIELD_SETTINGS.ENABLED = false;
    inspection.PACKAGE_SETTINGS.ENABLED = false;
    inspection.INNER_CLASS_SETTINGS.ENABLED = false;
    inspection.TOP_LEVEL_CLASS_SETTINGS.ENABLED = false;
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
    myInspection.METHOD_SETTINGS.ENABLED = true;
    doTest();
  }

  @Test void testPrivateMethodEnabled(){
    myInspection.METHOD_SETTINGS.ENABLED = true;
    myInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "private";
    doTest();
  }

  @Test void testMethodIgnored(){
    myInspection.METHOD_SETTINGS.ENABLED = true;
    myInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "private";
    myInspection.IGNORE_DEPRECATED_ELEMENTS = true;
    myInspection.IGNORE_ACCESSORS = true;
    doTest();
  }

  @Test void testPublicMemberInPrivateClass(){
    myInspection.METHOD_SETTINGS.ENABLED = true;
    myInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "public";
    myInspection.FIELD_SETTINGS.ENABLED = true;
    myInspection.FIELD_SETTINGS.MINIMAL_VISIBILITY = "public";
    doTest();
  }

  @Test void testClassEnabled(){
    myInspection.TOP_LEVEL_CLASS_SETTINGS.ENABLED = true;
    doTest();
  }

  @Test void testFieldEnabled(){
    myInspection.FIELD_SETTINGS.ENABLED = true;
    doTest();
  }

  @Test void testPrivateFieldEnabled(){
    myInspection.FIELD_SETTINGS.ENABLED = true;
    myInspection.FIELD_SETTINGS.MINIMAL_VISIBILITY = "private";
    doTest();
  }

  @Test void testInnerClassEnabled(){
    myInspection.INNER_CLASS_SETTINGS.ENABLED = true;
    doTest();
  }

  @Test void testProtectedInnerClassEnabled(){
    myInspection.INNER_CLASS_SETTINGS.ENABLED = true;
    myInspection.INNER_CLASS_SETTINGS.MINIMAL_VISIBILITY = "protected";
    doTest();
  }

  @Test void testRequiredTags(){
    myInspection.TOP_LEVEL_CLASS_SETTINGS.ENABLED = true;
    myInspection.TOP_LEVEL_CLASS_SETTINGS.setTagRequired("author", true);
    myInspection.TOP_LEVEL_CLASS_SETTINGS.setTagRequired("param", true);
    myInspection.METHOD_SETTINGS.ENABLED = true;
    doTest();
  }

  @Test void testPackageDisabled(){
    getFixture().testHighlighting("/packageInfo/disabled/package-info.java");
  }

  @Test void testPackageEnabled(){
    myInspection.PACKAGE_SETTINGS.ENABLED = true;
    getFixture().testHighlighting("/packageInfo/enabled/package-info.java");
  }

  @Test void testPackageRequiredTags(){
    myInspection.PACKAGE_SETTINGS.ENABLED = true;
    myInspection.PACKAGE_SETTINGS.setTagRequired("author", true);
    getFixture().testHighlighting("/packageInfo/requiredTags/package-info.java");
  }

  @Test void testModuleDisabled(){
    getFixture().testHighlighting("/moduleInfo/disabled/module-info.java");
  }

  @Test void testModuleEnabled(){
    myInspection.MODULE_SETTINGS.ENABLED = true;
    getFixture().testHighlighting("/moduleInfo/enabled/module-info.java");
  }

  @Test void testModuleRequiredTags(){
    myInspection.MODULE_SETTINGS.ENABLED = true;
    myInspection.MODULE_SETTINGS.setTagRequired("author", true);
    getFixture().testHighlighting("/moduleInfo/requiredTags/module-info.java");
  }

  private void doTest(){
    getFixture().testHighlighting(getTestName(false) + ".java");
  }
}
