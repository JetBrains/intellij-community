// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.navigation.MethodUpDownUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaMemberNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_include_anonymous_and_local_classes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection ResultOfObjectAllocationIgnored,override
    PsiFile file = myFixture.configureByText("a.java", """
      class Foo {
        void bar() {
          new Runnable() {
            void run() {}
          };
          class Local {
            void localMethod() {}
          }
        }
      }
      """);
    int[] offsets = ReadAction.computeBlocking(() -> MethodUpDownUtil.getNavigationOffsets(file, 0));
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("run")) >= 0);
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("Local")) >= 0);
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("localMethod")) >= 0);
  }

  @Test
  public void test_type_parameters_are_not_included() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.java", """
      class Foo {
        <T> void m1(T t) {}
      }
      """);
    int[] offsets = ReadAction.computeBlocking(() -> MethodUpDownUtil.getNavigationOffsets(file, 0));
    String typeParameterText = "<T>";
    int start = file.getText().indexOf(typeParameterText);
    int end = start + typeParameterText.length();
    for (int offset : offsets) {
      assertTrue(offset < start || offset > end);
    }
  }
}
