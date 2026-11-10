// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.usages;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.JavaUsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaUsageTypeProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/psi/usages/");
  }

  @Test
  public void testNestedClassAccess() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("NestedClassAccess.java", "Foo.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_NESTED_CLASS_ACCESS, myFixture.findClass("Foo")));
  }

  @Test
  public void testStaticMethodCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("StaticMethodCall.java", "Foo.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_STATIC_MEMBER_ACCESS, myFixture.findClass("Foo")));
  }

  @Test
  public void testStaticFieldUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("StaticFieldUsage.java", "Foo.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_STATIC_MEMBER_ACCESS, myFixture.findClass("Foo")));
  }

  @Test
  public void testStaticFieldUsageInImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("StaticFieldUsageInImport.java", "Foo.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_IMPORT, myFixture.findClass("Foo")));
  }

  @Test
  public void testStaticMethodUsageInImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("StaticMethodUsageInImport.java", "Foo.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_IMPORT, myFixture.findClass("Foo")));
  }

  @Test
  public void testMethodReferenceConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("MethodReferenceConstructor.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_NEW_OPERATOR, myFixture.findClass("Foo")));
  }

  @Test
  public void testPermitsClause() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("PermitsClause.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.CLASS_PERMITS_LIST, myFixture.findClass("Foo")));
  }

  @Test
  public void testPatternInSwitch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("PatternInSwitch.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.PATTERN, myFixture.findClass("Rec")));
  }

  @Test
  public void testPatternInInstanceof() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("PatternInInstanceof.java");
    ReadAction.runBlocking(() -> assertUsageType(UsageType.PATTERN, myFixture.findClass("Rec")));
  }

  private void assertUsageType(UsageType expected, PsiClass target) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    UsageTarget[] targets = {new PsiElement2UsageTargetAdapter(target)};
    PsiElement element = myFixture.getReferenceAtCaretPositionWithAssertion().getElement();
    UsageType usageType = new JavaUsageTypeProvider().getUsageType(element, targets);
    assertEquals(expected, usageType);
  }
}
