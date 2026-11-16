// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ExplicitToImplicitClassMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ExplicitToImplicitClassMigrationInspectionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/explicitToImplicitClassMigration/");
    testName = testInfo.getTestMethod().orElseThrow().getName().substring("test".length());
  }

  @Test
  public void testAnotherFile() { doNotFind(); }
  @Test
  public void testCaretAtClass() { doTest(); }
  @Test
  public void testExtendsObject() { doTest(); }
  @Test
  public void testInterface() { doNotFind(); }
  @Test
  public void testSeveralSimple() { doNotFind(); }
  @Test
  public void testSimple() { doTest(); }
  @Test
  public void testSimpleWithComments() { doTest(); }
  @Test
  public void testTestClass() { doNotFind(); }
  @Test
  public void testWithAnnotation() { doNotFind(); }
  @Test
  public void testWithConstructor() { doNotFind(); }
  @Test
  public void testWithEnhancedMain() { doTest(); }
  @Test
  public void testWithEnhancedMain2() { doTest(); }
  @Test
  public void testWithExtendList() { doNotFind(); }
  @Test
  public void testWithGeneric() { doNotFind(); }
  @Test
  public void testWithPackage() { doTest(); }
  @Test
  public void testWithPackageCheckMoving() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile sourceRoot = projectFixture.get().getSourceRoot();
    myFixture.configureByText("Test.java", """
        package foo.bar;
        
        class<caret> Test { public static void main(String[] args) {} }
        """);

    VirtualFile directory = myFixture.getFile().getVirtualFile().getParent();
    WriteAction.runAndWait(() -> {
      VirtualFile target = directory.createChildDirectory(this, "foo");
      myFixture.getFile().getVirtualFile().move(this, target);
    });
    String path = VfsUtilCore.getRelativePath(myFixture.getFile().getVirtualFile(), sourceRoot);
    assertEquals("foo/Test.java", path);
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention(JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(action);
      //several async actions
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
    path = VfsUtilCore.getRelativePath(myFixture.getFile().getVirtualFile(), sourceRoot);
    assertEquals("Test.java", path);
  }

  @Test
  public void testWithSyntaxError() { doNotFind(); }
  @Test
  public void testWithUsages() { doNotFind(); }
  @Test
  public void testWithImportConflict() {
    codeInsightFixture.get().addClass(
      """
        package p;
        public class List{}
        """
    );
    doTest();
  }

  @Test
  public void testWithImportConflictDemandsOverModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
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

  @Test
  public void testWithSeveralIO() { doTest(); }
  @Test
  public void testWithSeveralNestedIO() { doTest(); }

  private void doNotFind() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + testName + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(
      JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    assertNull(intention);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ExplicitToImplicitClassMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + testName + ".java");
    IntentionAction action = myFixture.findSingleIntention(JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name"));
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkPreviewAndLaunchAction(action));
    myFixture.checkResultByFile("after" + testName + ".java");
  }
}