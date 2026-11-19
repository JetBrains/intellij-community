// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class JavadocMethodResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_25);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testVerifiedJavadocParameterTypes() {
    checkJavadocParameterTypes("String", "java.lang.String");
    checkJavadocParameterTypes("java.util.List", "java.util.List<java.lang.String>");
    checkJavadocParameterTypes("java.util.List<String>", "java.util.List<java.lang.String>");
    checkJavadocParameterTypes("java.util.List<Integer>");
    checkJavadocParameterTypes("Integer"); // No method with Boxed int args in the snippet, expected
    checkJavadocParameterTypes("String[]", "java.lang.String[]");
    checkJavadocParameterTypes("String[][]"); // No method with [][] args in the snippet, expected
  }

  @Test
  public void testPartlyUnverifiedJavadocParameterTypes() {
    checkJavadocParameterTypes("String, diuuibbuibui", "java.lang.String, java.util.List<java.lang.String>");
    checkJavadocParameterTypes("diuuibbuibui, java.util.List<String>", "java.lang.String, java.util.List<java.lang.String>");
    checkJavadocParameterTypes("diuuibbuibui, java.util.List<Integer>");
    checkJavadocParameterTypes("diuuibbuibui, String");
    checkJavadocParameterTypes("diuuibbuibui, diuuibbuibui, diuuibbuibui");
  }

  @Test
  public void testPrimitiveJavadocParameterTypes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (var type : List.of( "boolean", "byte", "short", "int", "long", "char", "float", "double", "int[]", "float[]", "int...", "float...")) {
      myFixture.configureByText("Test.java", """
        class Test {
          /** @see #foo<caret>(%s) */
          void test() {}
          void foo(boolean value) {}
          void foo(byte value) {}
          void foo(short value) {}
          void foo(int value) {}
          void foo(long value) {}
          void foo(char value) {}
          void foo(float value) {}
          void foo(double value) {}
          void foo(int[] value) {}
          void foo(float[] value) {}
        }
        """.formatted(type));
      ReadAction.runBlocking(() -> {
        var reference = assertInstanceOf(PsiJavaReference.class, myFixture.getReferenceAtCaretPosition());
        var results = reference.multiResolve(false);
        assertEquals(1, results.length, () -> Arrays.toString(results));
        var method = assertInstanceOf(PsiMethod.class, results[0].getElement());
        assertEquals(type.replace("...", "[]"), method.getParameterList().getParameters()[0].getType().getCanonicalText());
      });
    }
  }

  private void checkJavadocParameterTypes(String parameterTypes, String... expectedParameters) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      class Test {
        /** @see #foo<caret>(%s) */
        void test() {}
        void foo(int value) {}
        void foo(String value) {}
        void foo(java.util.List<String> value) {}
        void foo(String[] value) {}
        void foo(String text, java.util.List<String> values) {}
        void other(String value) {}
      }
      """.formatted(parameterTypes));
    var resolvedParameters = ReadAction.computeBlocking(() -> {
      var reference = assertInstanceOf(PsiJavaReference.class, myFixture.getReferenceAtCaretPosition());
      return Arrays.stream(reference.multiResolve(false))
        .map(result -> {
          var method = assertInstanceOf(PsiMethod.class, result.getElement());
          assertEquals("foo", method.getName());
          return StringUtil.join(method.getParameterList().getParameters(), parameter -> parameter.getType().getCanonicalText(), ", ");
        })
        .toList();
    });
    assertThat(resolvedParameters).hasSize(expectedParameters.length);
    assertThat(new HashSet<>(resolvedParameters)).isEqualTo(new HashSet<>(Arrays.asList(expectedParameters)));
  }
}
