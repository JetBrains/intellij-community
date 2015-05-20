/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public class ConstantEvaluatorTest extends LightCodeInsightFixtureTestCase {

  public enum MyEnum { Foo, Bar }

  public void testEnum() {
    myFixture.addClass("package com.intellij.psi; public class ConstantEvaluatorTest { public enum MyEnum { Foo, Bar } }");
    myFixture.configureByText("Test.java", "import com.intellij.psi.ConstantEvaluatorTest; class Foo { ConstantEvaluatorTest.MyEnum e = ConstantEvaluatorTest.MyEnum.Foo; }");
    PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    PsiField field = psiClass.findFieldByName("e", false);
    assertNotNull(field);
    PsiExpression initializer = field.getInitializer();
    assertNotNull(initializer);

    Object result = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);

    assertEquals(MyEnum.Foo, result);
  }
}
