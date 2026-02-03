// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.navigation.MethodUpDownUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

public class JavaMemberNavigationTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_include_anonymous_and_local_classes() {
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
    int[] offsets = MethodUpDownUtil.getNavigationOffsets(file, 0);
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("run")) >= 0);
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("Local")) >= 0);
    assertTrue(ArrayUtil.indexOf(offsets, file.getText().indexOf("localMethod")) >= 0);
  }

  public void test_type_parameters_are_not_included() {
    PsiFile file = myFixture.configureByText("a.java", """
      class Foo {
        <T> void m1(T t) {}
      }
      """);
    int[] offsets = MethodUpDownUtil.getNavigationOffsets(file, 0);
    String typeParameterText = "<T>";
    int start = file.getText().indexOf(typeParameterText);
    int end = start + typeParameterText.length();
    for (int offset : offsets) {
      assertTrue(offset < start || offset > end);
    }
  }
}
