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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class InferredTypeTest extends LightCodeInsightFixtureTestCase {
  public void testNestedCallReturnType() throws Exception {
    myFixture.configureByText("a.java", "import java.util.List;\n" +
                                        "abstract class Test {\n" +
                                        "    abstract <R, K> R foo(K k1, K k2);\n" +
                                        "    {\n" +
                                        "        String str = \"\";\n" +
                                        "        List<String> l = f<caret>oo(foo(str, str), str);\n" +
                                        "    }\n" +
                                        "}\n");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    Assert.assertTrue(elementAtCaret instanceof PsiIdentifier);

    final PsiElement refExpr = elementAtCaret.getParent();
    Assert.assertTrue(refExpr.toString(), refExpr instanceof PsiExpression);
    final PsiType type = ((PsiExpression)refExpr).getType();
    Assert.assertNotNull(refExpr.toString(), type);
    Assert.assertTrue(type.getCanonicalText(), type.equalsToText("java.util.List<java.lang.String>"));
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
