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
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionalInterfaceSuggesterTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testPrimitiveReturnTypes() throws Exception {
    PsiClass aClass = myFixture.addClass("class Foo {double foo(double d) {return d;}}");
    List<String> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(aClass.getMethods()[0])
      .stream()
      .map(type -> type.getCanonicalText())
      .collect(Collectors.toList());
    Assert.assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.IntToDoubleFunction", 
                                                               "java.util.function.DoubleUnaryOperator")));
    Assert.assertFalse(suggestedTypes.contains("java.util.function.LongToIntFunction"));
  }
}
