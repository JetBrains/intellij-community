// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testIntegration;

import com.intellij.execution.junit.JUnit4Framework;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.testIntegration.createTest.JavaTestGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GenerateMissedTestsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testConflictingObjectNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass srcClass = myFixture.addClass("public class Source { @Override public int hashCode() { return 0;}}");
    PsiFile file = myFixture.configureByText("MyTest.java", "public class MyTest {}");
    PsiClass targetClass = ReadAction.computeBlocking(() -> ((PsiJavaFile)file).getClasses()[0]);
    List<MemberInfo> infos = ReadAction.computeBlocking(() -> TestIntegrationUtils.extractClassMethods(srcClass, true));
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                             () -> JavaTestGenerator.addTestMethods(myFixture.getEditor(),
                                                                                    targetClass, srcClass, framework,
                                                                                    infos, false, false));
  }
}
