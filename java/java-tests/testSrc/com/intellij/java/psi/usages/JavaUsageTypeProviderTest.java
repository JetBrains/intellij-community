// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.usages;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.JavaUsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;

public class JavaUsageTypeProviderTest extends LightJavaCodeInsightFixtureTestCase {
  
  public void testNestedClassAccess() {
    myFixture.configureByFiles("NestedClassAccess.java", "Foo.java");
    assertUsageType(UsageType.CLASS_NESTED_CLASS_ACCESS, myFixture.findClass("Foo"));
  }

  public void testStaticMethodCall() {
    myFixture.configureByFiles("StaticMethodCall.java", "Foo.java");
    assertUsageType(UsageType.CLASS_STATIC_MEMBER_ACCESS, myFixture.findClass("Foo"));
  }

  public void testStaticFieldUsage() {
    myFixture.configureByFiles("StaticFieldUsage.java", "Foo.java");
    assertUsageType(UsageType.CLASS_STATIC_MEMBER_ACCESS, myFixture.findClass("Foo"));
  }

  public void testStaticFieldUsageInImport() {
    myFixture.configureByFiles("StaticFieldUsageInImport.java", "Foo.java");
    assertUsageType(UsageType.CLASS_IMPORT, myFixture.findClass("Foo"));
  }

  public void testStaticMethodUsageInImport() {
    myFixture.configureByFiles("StaticMethodUsageInImport.java", "Foo.java");
    assertUsageType(UsageType.CLASS_IMPORT, myFixture.findClass("Foo"));
  }

  public void testMethodReferenceConstructor() {
    myFixture.configureByFiles("MethodReferenceConstructor.java");
    assertUsageType(UsageType.CLASS_NEW_OPERATOR, myFixture.findClass("Foo"));
  }

  public void testPermitsClause() {
    myFixture.configureByFiles("PermitsClause.java");
    assertUsageType(UsageType.CLASS_PERMITS_LIST, myFixture.findClass("Foo"));
  }

  public void testPatternInSwitch() {
    myFixture.configureByFiles("PatternInSwitch.java");
    assertUsageType(UsageType.PATTERN, myFixture.findClass("Rec"));
  }

  public void testPatternInInstanceof() {
    myFixture.configureByFiles("PatternInInstanceof.java");
    assertUsageType(UsageType.PATTERN, myFixture.findClass("Rec"));
  }

  private void assertUsageType(UsageType expected, PsiClass target) {
    UsageTarget[] targets = {new PsiElement2UsageTargetAdapter(target)};
    PsiElement element = myFixture.getReferenceAtCaretPositionWithAssertion().getElement();
    UsageType usageType = new JavaUsageTypeProvider().getUsageType(element, targets);
    assertEquals(expected, usageType);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/psi/usages/";
  }
}
