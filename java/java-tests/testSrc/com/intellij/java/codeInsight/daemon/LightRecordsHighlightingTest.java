// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.ReassignVariableUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightRecordsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingRecords";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  public void testRecordBasics() {
    doTest();
  }
  public void testRecordBasicsJava16() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }
  public void testRecordAccessors() {
    doTest();
  }
  public void testRecordConstructors() {
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
    myFixture.configureByText("A.java", "record Point(int x) {" +
                                        "    public Point {\n" +
                                        "        int x<caret>1 = 0\n" +
                                        "    }" + 
                                        "}");

    PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiDeclarationStatement.class);
    assertNotNull(decl);
    ReassignVariableUtil.registerDeclaration(getEditor(), decl, getTestRootDisposable());
    ReassignVariableUtil.reassign(getEditor());
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