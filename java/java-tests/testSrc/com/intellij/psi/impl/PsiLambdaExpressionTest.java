// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
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

  public void testLambdaInIfCondition() {
    // code is invalid, test that we don't throw exceptions
    myFixture.configureByText("Test.java", """
      class Test {
        void test() {
          if (<caret>s -> s.equalsIgnoreCase("foo")) { }
        }
      }
      """);
    PsiLambdaExpression lambda =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiLambdaExpression.class);
    assertNotNull(lambda);
    assertInstanceOf(lambda.getParameterList().getParameters()[0].getType(), PsiLambdaParameterType.class);
  }
}
