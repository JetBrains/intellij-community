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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(aClass.getMethods()[0]));
    assertEquals(4, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.ToDoubleFunction<java.lang.Double>",
                                                        "java.util.function.Function<java.lang.Double,java.lang.Double>",
                                                        "java.util.function.DoubleUnaryOperator",
                                                        "java.util.function.DoubleFunction<java.lang.Double>")));
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
    myFixture.addClass("class Foo { Foo() {} Foo(int value) {} }");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo::new; }}");
    final PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(8, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFactoryEmpty",
                                                        "MyFactoryInt",
                                                        "java.lang.Runnable",
                                                        "java.util.concurrent.Callable<Foo>",
                                                        "java.util.function.Function<java.lang.Integer,Foo>",
                                                        "java.util.function.IntConsumer",
                                                        "java.util.function.IntFunction<Foo>",
                                                        "java.util.function.Supplier<Foo>")));
  }

  public void testConstructorFactoryWithGenerics() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFactory<T, R> {\n" +
                       "    R create(T value);\n" +
                       "}\n");
    myFixture.addClass("import java.math.BigDecimal; class Foo { Foo() {} Foo(int value) {} Foo(BigDecimal value) {} }");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo::new; }}");
    final PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(9, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFactory<java.lang.Integer,Foo>",
                                                        "MyFactory<java.math.BigDecimal,Foo>",
                                                        "java.lang.Runnable",
                                                        "java.util.concurrent.Callable<Foo>",
                                                        "java.util.function.Function<java.lang.Integer,Foo>",
                                                        "java.util.function.Function<java.math.BigDecimal,Foo>",
                                                        "java.util.function.IntConsumer",
                                                        "java.util.function.IntFunction<Foo>",
                                                        "java.util.function.Supplier<Foo>")));
  }

  public void testConstructorFactoryWithGenericMethodThatIsNotSupported() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyInvalidFactory {\n" +
                       "    <T, R> R create(T value);\n" +
                       "}\n");
    myFixture.addClass("class Foo { Foo() {} Foo(int value) {} }");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo::new; }}");
    final PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(6, suggestedTypes.size());
    assertFalse(suggestedTypes.contains("MyInvalidFactory"));
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
    myFixture.addClass("import java.math.BigDecimal; class Foo { Foo() {} Foo(int value) {} Foo(BigDecimal value) {} }");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo::new; }}");
    PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(11, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFactoryNumber<java.lang.Integer,Foo>",
                                                        "MyFactoryNumber<java.math.BigDecimal,Foo>",
                                                        "MyFactoryReturnFoo<java.lang.Integer,Foo>",
                                                        "MyFactoryReturnFoo<java.math.BigDecimal,Foo>",
                                                        "java.lang.Runnable",
                                                        "java.util.concurrent.Callable<Foo>",
                                                        "java.util.function.Function<java.lang.Integer,Foo>",
                                                        "java.util.function.Function<java.math.BigDecimal,Foo>",
                                                        "java.util.function.IntConsumer",
                                                        "java.util.function.IntFunction<Foo>",
                                                        "java.util.function.Supplier<Foo>")));
    assertFalse(suggestedTypes.toString().contains("MyFactoryReturnNumber"));
    assertFalse(suggestedTypes.toString().contains("MyFactoryString"));
  }

  public void testSAMWithThrowable() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyThrowingConsumer<T> {\n" +
                       "    void accept(T t) throws Throwable;\n" +
                       "}\n");
    PsiMethod method = myFixture.addClass("class Foo { void foo(int i) {}}").getMethods()[0];
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(method, true));
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyThrowingConsumer<java.lang.Integer>",
                                                        "java.util.function.Consumer<java.lang.Integer>",
                                                        "java.util.function.IntConsumer")));
  }

  public void testMethodReferenceWithoutGenerics() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(int value);\n" +
                       "}\n");
    PsiMethod method = myFixture.addClass("class Foo { void foo(int i) {}}").getMethods()[0];
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(method, true));
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFunc",
                                                        "java.util.function.Consumer<java.lang.Integer>",
                                                        "java.util.function.IntConsumer")));
  }

  public void testMethodReferenceOfType() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(Foo foo, int value);\n" +
                       "}\n");
    myFixture.addClass("class Foo { void foo(int i) {}}");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo::foo; }}");
    PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFunc",
                                                        "java.util.function.BiConsumer<Foo,java.lang.Integer>",
                                                        "java.util.function.ObjIntConsumer<Foo>")));
  }

  public void testMethodReferenceOfInstance() {
    myFixture.addClass("@FunctionalInterface\n" +
                       "public interface MyFunc {\n" +
                       "    void accept(int value);\n" +
                       "}\n");
    myFixture.addClass("class Foo { void foo(int i) {}}");
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { Foo foo; foo::foo; }}");
    PsiMethodReferenceExpression expression = firstMethodReferenceFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("MyFunc",
                                                        "java.util.function.Consumer<java.lang.Integer>",
                                                        "java.util.function.IntConsumer")));
  }

  public void testLambdaExpression() {
    PsiClass mainClass = myFixture.addClass("class Main { static void main() { () -> 123; }}");
    PsiLambdaExpression expression = firstLambdaExpressionFromMain(mainClass);
    List<String> suggestedTypes = toCanonicalList(FunctionalInterfaceSuggester.suggestFunctionalInterfaces(expression));
    assertEquals(3, suggestedTypes.size());
    assertTrue(suggestedTypes.containsAll(Arrays.asList("java.util.function.DoubleSupplier",
                                                        "java.util.function.IntSupplier",
                                                        "java.util.function.LongSupplier")));
  }

  @Nullable
  private static PsiMethodReferenceExpression firstMethodReferenceFromMain(PsiClass mainClass) {
    return PsiTreeUtil.findChildOfType((PsiMethod)mainClass.findMethodsByName("main")[0], PsiMethodReferenceExpression.class);
  }

  @Nullable
  private static PsiLambdaExpression firstLambdaExpressionFromMain(PsiClass mainClass) {
    return PsiTreeUtil.findChildOfType((PsiMethod)mainClass.findMethodsByName("main")[0], PsiLambdaExpression.class);
  }

  private static List<String> toCanonicalList(Collection<? extends PsiType> types) {
    return types.stream()
      .map(PsiType::getCanonicalText)
      .collect(Collectors.toList());
  }
}
