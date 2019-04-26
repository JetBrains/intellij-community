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
import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FunctionalInterfaceSuggesterTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testPrimitiveReturnTypes() {
    PsiClass aClass = myFixture.addClass("class Foo {double foo(double d) {return d;}}");
    List<String> suggestedTypes =
      ContainerUtil.map(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(aClass.getMethods()[0]), PsiType::getCanonicalText);
    assertEquals(4, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.ToDoubleFunction<java.lang.Double>",
                                                        "java.util.function.Function<java.lang.Double,java.lang.Double>",
                                                        "java.util.function.DoubleUnaryOperator",
                                                        "java.util.function.DoubleFunction<java.lang.Double>")));
  }

  public void testOverloads() {
    PsiClass aClass = myFixture.addClass("class Foo {void foo(String s) {} void foo(int i) {}}");
    PsiExpression expression = getElementFactory().createExpressionFromText("Foo::foo", aClass);
    assertInstanceOf(expression, PsiMethodReferenceExpression.class);
    List<String> suggestedTypes = ContainerUtil
      .map(FunctionalInterfaceSuggester.suggestFunctionalInterfaces((PsiFunctionalExpression)expression), PsiType::getCanonicalText);
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.BiConsumer<Foo,java.lang.Integer>",
                                                        "java.util.function.BiConsumer<Foo,java.lang.String>",
                                                        "java.util.function.ObjIntConsumer<Foo>")));
  }

  public void testConstructorFactory() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryEmpty {\n" +
                       "    Foo create();\n" +
                       "}\n");
     myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryInt {\n" +
                       "    Foo create(int value);\n" +
                       "}\n");
    Collection<? extends PsiType> suggestedTypes = suggestTypes("class Foo { Foo() {} Foo(int value) {} }", "Foo::new");
    checkWithExpected(suggestedTypes, "MyFactoryEmpty",
                      "MyFactoryInt",
                      "java.lang.Runnable",
                      "java.util.concurrent.Callable<Foo>",
                      "java.util.function.Function<java.lang.Integer,Foo>",
                      "java.util.function.IntConsumer",
                      "java.util.function.IntFunction<Foo>",
                      "java.util.function.Supplier<Foo>");
  }

  public void testConstructorFactoryWithGenerics() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactory<T, R> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    Collection<? extends PsiType> suggestedTypes =
      suggestTypes("import java.math.BigDecimal; class Foo { Foo() {} Foo(int value) {} Foo(BigDecimal value) {} }", "Foo::new");
    checkWithExpected(suggestedTypes,"MyFactory<java.lang.Integer,Foo>",
                                                        "MyFactory<java.math.BigDecimal,Foo>",
                                                        "java.lang.Runnable",
                                                        "java.util.concurrent.Callable<Foo>",
                                                        "java.util.function.Function<java.lang.Integer,Foo>",
                                                        "java.util.function.Function<java.math.BigDecimal,Foo>",
                                                        "java.util.function.IntConsumer",
                                                        "java.util.function.IntFunction<Foo>",
                                                        "java.util.function.Supplier<Foo>");
  }

  public void testConstructorFactoryWithGenericMethodThatIsNotSupported() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyInvalidFactory {\n" +
                       "    <T, R> R create(T value);\n" +
                       "}\n");
    Collection<? extends PsiType> suggestedTypes =
      suggestTypes("class Foo { Foo() {} Foo(int value) {} }", "Foo::new");
    assertEquals(6, suggestedTypes.size());
    assertFalse(suggestedTypes.stream().anyMatch(type -> "MyInvalidFactory".equals(type.getCanonicalText())));
  }

  public void testConstructorFactoryWithGenericsSubTypes() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryNumber<T extends Number, R> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryString<T extends String, R> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryReturnFoo<T, R extends Foo> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactoryReturnNumber<T, R extends Number> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    Collection<? extends PsiType> suggestedTypes =
      suggestTypes("import java.math.BigDecimal; class Foo { Foo() {} Foo(int value) {} Foo(BigDecimal value) {} }", "Foo::new");
    checkWithExpected(suggestedTypes,"MyFactoryNumber<java.lang.Integer,Foo>",
                                                        "MyFactoryNumber<java.math.BigDecimal,Foo>",
                                                        "MyFactoryReturnFoo<java.lang.Integer,Foo>",
                                                        "MyFactoryReturnFoo<java.math.BigDecimal,Foo>",
                                                        "java.lang.Runnable",
                                                        "java.util.concurrent.Callable<Foo>",
                                                        "java.util.function.Function<java.lang.Integer,Foo>",
                                                        "java.util.function.Function<java.math.BigDecimal,Foo>",
                                                        "java.util.function.IntConsumer",
                                                        "java.util.function.IntFunction<Foo>",
                                                        "java.util.function.Supplier<Foo>");
    assertFalse(suggestedTypes.stream().anyMatch(type -> "MyFactoryReturnNumber".equals(type.getCanonicalText())));
    assertFalse(suggestedTypes.stream().anyMatch(type -> "MyFactoryString".equals(type.getCanonicalText())));
  }

  public void testSAMWithThrowable() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyThrowingConsumer<T> {\n" +
                       "    void accept(T t) throws Throwable;\n" +
                       "}\n");
    PsiMethod method = myFixture.addClass("class Foo { void foo(int i) {}}").getMethods()[0];
    Collection<? extends PsiType> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(method, true);
    checkWithExpected(suggestedTypes,"MyThrowingConsumer<java.lang.Integer>",
                                                        "java.util.function.Consumer<java.lang.Integer>",
                                                        "java.util.function.IntConsumer");
  }

  public void testMethodReferenceWithoutGenerics() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(int value);\n" +
                       "}\n");
    PsiMethod method = myFixture.addClass("class Foo { void foo(int i) {}}").getMethods()[0];
    Collection<? extends PsiType> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(method, true);
    checkWithExpected(suggestedTypes, "MyFunc",
                                                        "java.util.function.Consumer<java.lang.Integer>",
                                                        "java.util.function.IntConsumer");
  }

  public void testMethodReferenceOfType() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(Foo foo, int value);\n" +
                       "}\n");
    Collection<? extends PsiType> suggestedTypes = suggestTypes("class Foo { void foo(int i) {}}", "Foo::foo");
    checkWithExpected(suggestedTypes, "MyFunc",
                      "java.util.function.BiConsumer<Foo,java.lang.Integer>",
                      "java.util.function.ObjIntConsumer<Foo>");
  }

  public void testMethodReferenceOfInstance() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(int value);\n" +
                       "}\n");
    PsiClass fooClass = myFixture.addClass("class Foo { void foo(int i) {}}");
    PsiDeclarationStatement fooVar =
      getElementFactory().createVariableDeclarationStatement("foo", getElementFactory().createType(fooClass), null, fooClass);
    PsiExpression methodReference = getElementFactory().createExpressionFromText("foo::foo", fooVar);
    assertInstanceOf(methodReference, PsiMethodReferenceExpression.class);
    final PsiMethodReferenceExpression expression = (PsiMethodReferenceExpression)methodReference;
    Collection<? extends PsiType> suggestedTypes = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression);
    checkWithExpected(suggestedTypes, "MyFunc",
      "java.util.function.Consumer<java.lang.Integer>",
      "java.util.function.IntConsumer");
  }

  public void testLambdaExpression() {
    PsiLambdaExpression expression = (PsiLambdaExpression)getElementFactory().createExpressionFromText("() -> 123", myFixture.addClass("class Empty{}"));
    checkWithExpected(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression),
                      "java.util.concurrent.Callable<java.lang.Integer>",
                      "java.util.function.DoubleSupplier",
                      "java.util.function.IntSupplier",
                      "java.util.function.LongSupplier",
                      "java.util.function.Supplier<java.lang.Integer>");
  }

  private static void checkWithExpected(Collection<? extends PsiType> suggestedTypes, final String... expectedTypes) {
    assertEquals("Suggested types: " + suggestedTypes.toString(), expectedTypes.length, suggestedTypes.size());
    assertTrue(ContainerUtil.map(suggestedTypes, PsiType::getCanonicalText).containsAll(Arrays.asList(expectedTypes)));
  }

  private Collection<? extends PsiType> suggestTypes(@Language("JAVA") String fooClassText, String functionalExpressionText) {
    PsiClass fooClass = myFixture.addClass(fooClassText);
    PsiExpression expression1 = getElementFactory().createExpressionFromText(functionalExpressionText, fooClass);
    assertInstanceOf(expression1, PsiMethodReferenceExpression.class);
    final PsiMethodReferenceExpression expression = (PsiMethodReferenceExpression)expression1;
    return FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression);
  }
}
