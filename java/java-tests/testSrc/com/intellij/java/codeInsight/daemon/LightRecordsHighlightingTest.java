// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.idea.TestFor;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.ReassignVariableUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LightRecordsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingRecords");
  }

  @Test
  public void testRecordBasics() {
    doTest();
  }
  @Test
  public void testRecordBasicsJava16() {
    doTest();
  }
  @Test
  public void testRecordAccessors() {
    doTest();
  }
  @Test
  public void testRecordConstructors() {
    doTest();
  }
  @Test
  public void testRecordConstructors2() {
    doTest();
  }
  @Test
  public void testRecordConstructorAccessJava15() {
    doTest();
  }
  @Test
  public void testRecordCompactConstructors() {
    doTest();
  }

  @Test
  @TestFor(issues = "IDEA-393876")
  public void testVarArgsCompactConstructorFromStub() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         public record VarArgsRecord(String... xs) {
                           public VarArgsRecord { xs = xs.clone(); }
                         }""");
    myFixture.configureByText("B.java", """
      class B {
        void test() {
          new VarArgsRecord();
          new VarArgsRecord("x");
          new VarArgsRecord("x", "y");
        }
      }""");
    myFixture.checkHighlighting();
  }

  @Test
  public void testLocalRecords() {
    doTest();
  }
  @Test
  public void testReassignToRecordComponentsDisabled() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.lang; public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();" +
                       "}");
    myFixture.configureByText("A.java", """
      record Point(int x) {    public Point {
              int x<caret>1 = 0
          }}""");

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiDeclarationStatement.class);
      assertNotNull(decl);
      ReassignVariableUtil.registerDeclaration(myFixture.getEditor(), decl, myFixture.getTestRootDisposable());
      ReassignVariableUtil.reassign(myFixture.getEditor());
    });
  }
  @Test
  public void testModifiersInsideAnonymousLocal() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.lang; public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();" +
                       "}");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}