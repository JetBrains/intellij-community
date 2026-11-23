// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.generation;

import com.intellij.codeInsight.generation.OverrideImplementMethodsModCommandAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class OverrideImplementMethodsModCommandActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void implementInNewExpression() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        void test() {
          Object o = new I<String>()<caret>;
        }
      }
      interface I<T> {
        void foo(T t);

        int bar();
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.launchAction(OverrideImplementMethodsModCommandAction.implement(findNewExpression(fixture)).asIntention());
      fixture.checkResult("""
        class Test {
          void test() {
            Object o = new I<String>() {
                @Override
                public void foo(String s) {
                   \s
                }

                @Override
                public int bar() {
                    return 0;
                }
            };
          }
        }
        interface I<T> {
          void foo(T t);

          int bar();
        }
        """);
    });
  }

  @Test
  public void notAvailableInIncompleteNewExpression() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        void test() {
          new I<String>(new String(<caret>
        }
      }
      interface I<T> {
        void foo(T t);
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiNewExpression newExpression = findNewExpression(fixture);
      ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
      assertNull(OverrideImplementMethodsModCommandAction.implement(newExpression).getPresentation(context));
    });
  }

  private static @NotNull PsiNewExpression findNewExpression(@NotNull LightJavaCodeInsightTestFixture fixture) {
    PsiNewExpression newExpression = ReadAction.computeBlocking(
      () -> PsiTreeUtil.findChildOfType(fixture.getFile(), PsiNewExpression.class));
    assertNotNull(newExpression);
    return newExpression;
  }
}
