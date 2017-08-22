/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.usages;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.JavaUsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;

/**
 * @author nik
 */
public class JavaUsageTypeProviderTest extends LightCodeInsightFixtureTestCase {
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
