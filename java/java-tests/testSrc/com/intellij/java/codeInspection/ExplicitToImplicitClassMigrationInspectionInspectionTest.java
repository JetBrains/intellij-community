// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ExplicitToImplicitClassMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ExplicitToImplicitClassMigrationInspectionInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/explicitToImplicitClassMigration/";
  }

  public void testAnotherFile() { doNotFind(); }
  public void testCaretAtClass() { doTest(); }
  public void testExtendsObject() { doTest(); }
  public void testInterface() { doNotFind(); }
  public void testSeveralSimple() { doNotFind(); }
  public void testSimple() { doTest(); }
  public void testSimpleWithComments() { doTest(); }
  public void testTestClass() { doNotFind(); }
  public void testWithAnnotation() { doNotFind(); }
  public void testWithConstructor() { doNotFind(); }
  public void testWithEnhancedMain() { doTest(); }
  public void testWithEnhancedMain2() { doTest(); }
  public void testWithExtendList() { doNotFind(); }
  public void testWithGeneric() { doNotFind(); }
  public void testWithPackage() { doNotFind(); }
  public void testWithSyntaxError() { doNotFind(); }
  public void testWithUsages() { doNotFind(); }
  public void testWithImportConflict() {
    myFixture.addClass(
      """
        package p;
        public class List{}
        """
    );
    doTest();
  }

  public void testWithImportConflictDemandsOverModule() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
       myFixture.addClass(
         """
           package p;
           public class List{}
           """
       );
       doTest();
     }
    );
  }

  private void doNotFind() {
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(
      JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    assertNull(intention);
  }

  private void doTest() {
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
      JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name")));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}