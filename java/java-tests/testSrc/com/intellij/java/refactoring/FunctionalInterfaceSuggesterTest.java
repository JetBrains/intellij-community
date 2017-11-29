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
package com.intellij.java.refactoring;

import com.intellij.codeInsight.FunctionalInterfaceSuggester;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionalInterfaceSuggesterTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testPrimitiveReturnTypes() {
    PsiClass aClass = myFixture.addClass("class Foo {double foo(double d) {return d;}}");
    List<String> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(aClass.getMethods()[0])
      .stream()
      .map(type -> type.getCanonicalText())
      .collect(Collectors.toList());
    assertEquals(4, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.ToDoubleFunction<java.lang.Double>",
                                                        "java.util.function.DoubleUnaryOperator")));
  }

  public void testSAMWithThrowable() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyThrowingConsumer<T> {\n" +
                       "    void accept(T t) throws Throwable;\n" +
                       "}\n");
    PsiMethod method = myFixture.addClass("class Foo { void foo(int i) {}}").getMethods()[0];
    Collection<? extends PsiType> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(method, true);
    assertTrue(suggestedTypes.stream().anyMatch(type -> type.equalsToText("MyThrowingConsumer<Integer>")));
  }
}
