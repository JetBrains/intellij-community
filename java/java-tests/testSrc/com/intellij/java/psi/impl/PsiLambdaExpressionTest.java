// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public final class PsiLambdaExpressionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testLambdaParameterTypeMultiResolve() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      import java.util.stream.*;
      
      class Test {
        void test(Stream<String> stream) {
          stream.collect(Collectors.toMap(t -> <caret>t))
        }
      }
      """
    );
    ReadAction.runBlocking(() -> {
      PsiReferenceExpression ref =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
      assertNotNull(ref);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }
  
  @Test
  public void testLambdaParameterTypeMultiResolveDifferentFunctions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiReferenceExpression ref =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
      assertNotNull(ref);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testLambdaParameterTypeMultiResolveConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiReferenceExpression ref =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
      assertNotNull(ref);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testLambdaParameterTypeMultiResolveConstructorDiamond() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiReferenceExpression ref =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiReferenceExpression.class);
      assertNotNull(ref);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testLambdaInIfCondition() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // code is invalid, test that we don't throw exceptions
    myFixture.configureByText("Test.java", """
      class Test {
        void test() {
          if (<caret>s -> s.equalsIgnoreCase("foo")) { }
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiLambdaExpression lambda =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiLambdaExpression.class);
      assertNotNull(lambda);
      assertInstanceOf(PsiLambdaParameterType.class, lambda.getParameterList().getParameters()[0].getType());
    });
  }
}
