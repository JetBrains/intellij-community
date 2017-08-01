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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DataFlowInspection8Test extends DataFlowInspectionTestCase {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk18());
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testAnnotatedTypeParameters() { doTestWithCustomAnnotations(); }
  public void testReturnNullInLambdaExpression() { doTest(); }
  public void testReturnNullInLambdaStatement() { doTest(); }
  public void testUnboxingBoxingInLambdaReturn() { doTest(); }
  public void testUnboxingInMethodReferences() { doTest(); }
  public void testMethodReferenceOnNullable() { doTest(); }
  public void testNullableVoidLambda() { doTest(); }
  public void testNullableForeachVariable() { doTestWithCustomAnnotations(); }
  public void testGenericParameterNullity() { doTestWithCustomAnnotations(); }
  public void testMethodReferenceConstantValue() { doTestWithCustomAnnotations(); }

  public void testOptionalOfNullable() { doTest(); }
  public void testOptionalOrElse() { doTest(); }
  public void testOptionalIsPresent() { doTest(); }
  public void testOptionalGetWithoutIsPresent() {
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    myFixture.addClass("package org.testng;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    addGuava();
    doTest();
  }

  private void addGuava() {
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Supplier<T> { T get();}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Function<F, T> { T apply(F input);}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public abstract class Optional<T> {\n" +
                       "  public static <T> Optional<T> absent() {}\n" +
                       "  public static <T> Optional<T> of(T ref) {}\n" +
                       "  public static <T> Optional<T> fromNullable(T ref) {}\n" +
                       "  public abstract T get();\n" +
                       "  public abstract boolean isPresent();\n" +
                       "  public abstract T orNull();\n" +
                       "  public abstract T or(Supplier<? extends T> supplier);\n" +
                       "  public abstract <V> Optional<V> transform(Function<? super T, V> fn);\n" +
                       "  public abstract T or(T val);\n" +
                       "  public abstract java.util.Optional<T> toJavaUtil();\n" +
                       "}");
  }

  public void testPrimitiveInVoidLambda() { doTest(); }
  public void testNotNullLambdaParameter() { doTest(); }
  public void testNotNullOptionalLambdaParameter() { doTest(); }

  public void testNullArgumentIsFailingMethodCall() {
    doTest();
  }

  public void testNullArgumentIsNotFailingMethodCall() {
    doTest();
  }

  public void testNullArgumentButParameterIsReassigned() {
    doTest();
  }

  public void testNullableArrayComponent() {
    setupCustomAnnotations();
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testDontSuggestToMakeLambdaNullable() {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testLambdaParametersWithDefaultNullability() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  private void doTestWithCustomAnnotations() {
    setupCustomAnnotations();
    doTest();
  }

  private void setupCustomAnnotations() {
    setupTypeUseAnnotations("foo", myFixture);
  }

  static void setupTypeUseAnnotations(String pkg, JavaCodeInsightTestFixture fixture) {
    setupCustomAnnotations(pkg, "{ElementType.TYPE_USE}", fixture);
  }

  private static void setupCustomAnnotations(String pkg, String target, JavaCodeInsightTestFixture fixture) {
    fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface Nullable { }");
    fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface NotNull { }");
    setCustomAnnotations(fixture.getProject(), fixture.getTestRootDisposable(), pkg + ".NotNull", pkg + ".Nullable");
  }

  static void setCustomAnnotations(Project project, Disposable parentDisposable, String notNull, String nullable) {
    NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(project);
    nnnManager.setNotNulls(notNull);
    nnnManager.setNullables(nullable);
    Disposer.register(parentDisposable, () -> {
      nnnManager.setNotNulls();
      nnnManager.setNullables();
    });
  }

  public void testCapturedWildcardNotNull() { doTest(); }
  public void testVarargNotNull() { doTestWithCustomAnnotations(); }
  public void testIgnoreNullabilityOnPrimitiveCast() { doTestWithCustomAnnotations();}

  public void testArrayComponentAndMethodAnnotationConflict() {
    setupCustomAnnotations("withTypeUse", "{ElementType.METHOD, ElementType.TYPE_USE}", myFixture);
    doTest();
  }

  public void testLambdaInlining() { doTest(); }

  public void testOptionalInlining() {
    addGuava();
    doTest();
  }
  public void testStreamInlining() { doTest(); }

  public void testMethodVsExpressionTypeAnnotationConflict() {
    setupCustomAnnotations("withTypeUse", "{ElementType.METHOD, ElementType.TYPE_USE}", myFixture);
    doTest();
  }

}