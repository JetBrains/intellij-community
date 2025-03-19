// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class PsiLambdaExpressionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testLambdaParameterTypeMultiResolve() {
    myFixture.configureByText("Test.java", """
      import java.util.stream.*;
      
      class Test {
        void test(Stream<String> stream) {
          stream.collect(Collectors.toMap(t -> <caret>t))
        }
      }
      """
    );
    PsiReferenceExpression ref =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
    assertNotNull(ref);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }
  
  public void testLambdaParameterTypeMultiResolveDifferentFunctions() {
    myFixture.configureByText("Test.java", """
      import java.util.function.Consumer;
      import java.util.function.Function;
      
      class Test {
          void foo(Consumer<String> cons, int a) {}
          void foo(Function<String, String> cons, int a, int b) {}
          void foo(Consumer<String> cons, int a, int b, int c) {}
      
          void test() {
              foo(s -> <caret>s);
          }
      }
      """);
    PsiReferenceExpression ref =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
    assertNotNull(ref);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testLambdaParameterTypeMultiResolveConstructor() {
    myFixture.configureByText("Test.java", """
      import java.util.function.Consumer;
      import java.util.function.Function;
      
      class Test {
          Test(Consumer<String> cons, int a) {}
          Test(Function<String, String> cons, int a, int b) {}
          Test(Consumer<String> cons, int a, int b, int c) {}
      
          Object test() {
              return new Test(s -> <caret>s);
          }
      }
      """);
    PsiReferenceExpression ref =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
    assertNotNull(ref);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testLambdaParameterTypeMultiResolveConstructorDiamond() {
    myFixture.configureByText("Test.java", """
      import java.util.function.Consumer;
      import java.util.function.Function;
      
      class Test<T> {
          Test(Consumer<T> cons, int a) {}
          Test(Function<T, String> cons, int a, int b) {}
          Test(Consumer<T> cons, int a, int b, int c) {}
      
          Test<String> test() {
              return new Test<>(s -> <caret>s);
          }
      }
      """);
    PsiReferenceExpression ref =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
    assertNotNull(ref);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }
}
