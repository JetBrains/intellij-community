// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class JavadocMethodResolveTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  public void testVerifiedJavadocParameterTypes() {
    checkJavadocParameterTypes("String", "java.lang.String");
    checkJavadocParameterTypes("java.util.List", "java.util.List<java.lang.String>");
    checkJavadocParameterTypes("java.util.List<String>", "java.util.List<java.lang.String>");
    checkJavadocParameterTypes("java.util.List<Integer>");
    checkJavadocParameterTypes("Integer"); // No method with Boxed int args in the snippet, expected
    checkJavadocParameterTypes("String[]", "java.lang.String[]");
    checkJavadocParameterTypes("String[][]"); // No method with [][] args in the snippet, expected
  }

  public void testPartlyUnverifiedJavadocParameterTypes() {
    checkJavadocParameterTypes("String, diuuibbuibui", "java.lang.String, java.util.List<java.lang.String>");
    checkJavadocParameterTypes("diuuibbuibui, java.util.List<String>", "java.lang.String, java.util.List<java.lang.String>");
    checkJavadocParameterTypes("diuuibbuibui, java.util.List<Integer>");
    checkJavadocParameterTypes("diuuibbuibui, String");
    checkJavadocParameterTypes("diuuibbuibui, diuuibbuibui, diuuibbuibui");
  }

  public void testPrimitiveJavadocParameterTypes() {
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
      var reference = assertInstanceOf(myFixture.getReferenceAtCaretPosition(), PsiJavaReference.class);
      var results = reference.multiResolve(false);
      assertSize(1, results);
      var method = assertInstanceOf(results[0].getElement(), PsiMethod.class);
      assertEquals(type.replace("...", "[]"), method.getParameterList().getParameters()[0].getType().getCanonicalText());
    }
  }

  private void checkJavadocParameterTypes(String parameterTypes, String... expectedParameters) {
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
    var reference = assertInstanceOf(myFixture.getReferenceAtCaretPosition(), PsiJavaReference.class);
    var resolvedParameters = Arrays.stream(reference.multiResolve(false))
      .map(result -> {
        var method = assertInstanceOf(result.getElement(), PsiMethod.class);
        assertEquals("foo", method.getName());
        return StringUtil.join(method.getParameterList().getParameters(), parameter -> parameter.getType().getCanonicalText(), ", ");
      })
      .toList();
    assertSameElements(resolvedParameters, expectedParameters);
  }
}
