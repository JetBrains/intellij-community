// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.refactoring.introduceVariable.*;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.*;

public class LightRecordsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingRecords";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testRecordBasics() {
    doTest();
  }
  public void testRecordBasicsJava16() {
    doTest();
  }
  public void testRecordAccessors() {
    doTest();
  }
  public void testRecordConstructors() {
    doTest();
  }
  public void testRecordConstructors2() {
    doTest();
  }
  public void testRecordConstructorAccessJava15() {
    doTest();
  }
  public void testRecordCompactConstructors() {
    doTest();
  }
  public void testLocalRecords() {
    doTest();
  }
  public void testReassignToRecordComponentsDisabled() {
    myFixture.addClass("package java.lang; public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();" +
                       "}");
    myFixture.configureByText("A.java", """
      record Point(int x) {    public Point {
              int x<caret>1 = 0
          }}""");

    PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiDeclarationStatement.class);
    assertNotNull(decl);
    ReassignVariableUtil.registerDeclaration(getEditor(), decl, getTestRootDisposable());
    ReassignVariableUtil.reassign(getEditor());
  }
  public void testModifiersInsideAnonymousLocal() {
    doTest();
  }

  private void doTest() {
    myFixture.addClass("package java.lang; public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}