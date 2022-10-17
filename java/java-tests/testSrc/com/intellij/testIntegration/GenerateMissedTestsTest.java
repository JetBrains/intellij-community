// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration;

import com.intellij.execution.junit.JUnit4Framework;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.createTest.JavaTestGenerator;

import java.util.List;

public class GenerateMissedTestsTest extends LightJavaCodeInsightFixtureTestCase {
  public void testConflictingObjectNames() {
    PsiClass srcClass = myFixture.addClass("public class Source { @Override public int hashCode() { return 0;}}");
    PsiClass targetClass = ((PsiJavaFile)myFixture.configureByText("MyTest.java", "public class MyTest {}")).getClasses()[0];
    List<MemberInfo> infos = TestIntegrationUtils.extractClassMethods(srcClass, true);
    doTest(srcClass, targetClass, infos, new JUnit4Framework());
    myFixture.checkResult("""
                            public class MyTest {
                                @org.junit.Test
                                public void testHashCode() {
                                }
                            }""", true);
    //repeat
    doTest(srcClass, targetClass, infos, new JUnit4Framework());
    myFixture.checkResult("""
                            public class MyTest {
                                @org.junit.Test
                                public void testHashCode() {
                                }

                                @org.junit.Test
                                public void testHashCode1() {
                                }
                            }""", true);
  }

  private void doTest(PsiClass srcClass, PsiClass targetClass, List<MemberInfo> infos, JUnit4Framework framework) {
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                             () -> JavaTestGenerator.addTestMethods(getEditor(),
                                                                                    targetClass, srcClass, framework,
                                                                                    infos, false, false));
  }
}
