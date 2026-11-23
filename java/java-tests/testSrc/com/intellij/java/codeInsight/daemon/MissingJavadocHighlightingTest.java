// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/daemonCodeAnalyzer/missingJavadoc/")
@TestApplication
public class MissingJavadocHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private MissingJavadocInspection myInspection;

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/missingJavadoc/");
    MissingJavadocInspection inspection = new MissingJavadocInspection();
    inspection.METHOD_SETTINGS.ENABLED = false;
    inspection.MODULE_SETTINGS.ENABLED = false;
    inspection.FIELD_SETTINGS.ENABLED = false;
    inspection.PACKAGE_SETTINGS.ENABLED = false;
    inspection.INNER_CLASS_SETTINGS.ENABLED = false;
    inspection.TOP_LEVEL_CLASS_SETTINGS.ENABLED = false;
    codeInsightFixture.get().enableInspections(inspection);
    myInspection = inspection;
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
    codeInsightFixture.get().testHighlighting("/packageInfo/disabled/package-info.java");
  }

  @Test void testPackageEnabled(){
    myInspection.PACKAGE_SETTINGS.ENABLED = true;
    codeInsightFixture.get().testHighlighting("/packageInfo/enabled/package-info.java");
  }

  @Test void testPackageRequiredTags(){
    myInspection.PACKAGE_SETTINGS.ENABLED = true;
    myInspection.PACKAGE_SETTINGS.setTagRequired("author", true);
    codeInsightFixture.get().testHighlighting("/packageInfo/requiredTags/package-info.java");
  }

  @Test void testModuleDisabled(){
    codeInsightFixture.get().testHighlighting("/moduleInfo/disabled/module-info.java");
  }

  @Test void testModuleEnabled(){
    myInspection.MODULE_SETTINGS.ENABLED = true;
    codeInsightFixture.get().testHighlighting("/moduleInfo/enabled/module-info.java");
  }

  @Test void testModuleRequiredTags(){
    myInspection.MODULE_SETTINGS.ENABLED = true;
    myInspection.MODULE_SETTINGS.setTagRequired("author", true);
    codeInsightFixture.get().testHighlighting("/moduleInfo/requiredTags/module-info.java");
  }

  private void doTest(){
    codeInsightFixture.get().testHighlighting(codeInsightFixture.get().getTestName(false) + ".java");
  }
}
