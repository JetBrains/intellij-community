// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ExplicitToImplicitClassMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ExplicitToImplicitClassMigrationInspectionInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
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
  public void testWithPackage() { doTest(); }
  public void testWithPackageCheckMoving() throws IOException {
    myFixture.configureByText("Test.java", """
        package foo.bar;
        
        class<caret> Test { public static void main(String[] args) {} }
        """);

    VirtualFile directory = myFixture.getFile().getVirtualFile().getParent();
    WriteAction.runAndWait(() -> {
      VirtualFile target = directory.createChildDirectory(this, "foo");
      myFixture.getFile().getVirtualFile().move(this, target);
    });
    String url = myFixture.getFile().getVirtualFile().getUrl();
    assertEquals("temp:///src/foo/Test.java", url);
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention(JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    myFixture.launchAction(action);
    //several async actions
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    url = myFixture.getFile().getVirtualFile().getUrl();
    assertEquals("temp:///src/Test.java", url);
  }

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
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
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

  public void testWithSeveralIO() { doTest(); }
  public void testWithSeveralNestedIO() { doTest(); }

  private void doNotFind() {
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(
      JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    assertNull(intention);
  }

  private void doTest() {
    MigrateToJavaLangIoInspectionTest.addIOClass(myFixture);
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
      JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name")));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}