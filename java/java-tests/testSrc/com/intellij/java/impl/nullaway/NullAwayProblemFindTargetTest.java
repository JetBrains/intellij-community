// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;

import static com.intellij.java.impl.nullaway.NullAwayProblem.Kind.ASSIGNING_NULLABLE_TO_NONNULL_FIELD;


public class NullAwayProblemFindTargetTest extends JavaCodeInsightFixtureTestCase {
  public void testFindFieldTarget() {
    Path filePath = configureFile(
      """
        public class MyClass {
          private String targetField;
        }
        """);

    var nullAwayProblem = new NullAwayProblem(filePath, 1, ASSIGNING_NULLABLE_TO_NONNULL_FIELD);
    assertFieldTargetFound(nullAwayProblem, "targetField");
  }

  public void testFindMethodTarget() {
    Path filePath = configureFile(
      """
        public class MyClass {
          public String someMethod(String s) {
            return s;
          }
        }
        """);

    var nullAwayProblem = new NullAwayProblem(filePath, 2, ASSIGNING_NULLABLE_TO_NONNULL_FIELD);
    assertMethodTargetFound(nullAwayProblem, "someMethod");
  }

  public void testFindClassTargetForStaticInitializer() {
    Path filePath = configureFile(
      """
        public class MyClass {
          String nullable = null;
          @NonNull nonNull;
          static {
            nonNull = nullable;
          }
        }
        """);

    var nullAwayProblem = new NullAwayProblem(filePath, 4, ASSIGNING_NULLABLE_TO_NONNULL_FIELD);
    assertClassTargetFound(nullAwayProblem, "MyClass");
  }

  private @Nullable Path configureFile(@Language("JAVA") String text) {
    PsiFile file = myFixture.configureByText("MyClass.java", text);
    String canonicalPath = file.getVirtualFile().getCanonicalPath();
    return canonicalPath != null ? Path.of(canonicalPath) : null;
  }

  private void assertFieldTargetFound(NullAwayProblem nullAwayProblem, String fieldName) {
    var target = nullAwayProblem.findSuppressionTarget(getProject());
    if (target instanceof PsiField psiField) {
      assertEquals(fieldName, psiField.getName());
    }
    else {
      Assertions.fail("Expected target to be of PsiField type but got " + (target == null ? "null" : target.getClass().getName()));
    }
  }

  private void assertMethodTargetFound(NullAwayProblem nullAwayProblem, String methodName) {
    var target = nullAwayProblem.findSuppressionTarget(getProject());
    if (target instanceof PsiMethod psiMethod) {
      assertEquals(methodName, psiMethod.getName());
    }
    else {
      Assertions.fail("Expected target to be of PsiMethod type but got " + (target == null ? "null" : target.getClass().getName()));
    }
  }

  private void assertClassTargetFound(NullAwayProblem nullAwayProblem, String className) {
    var target = nullAwayProblem.findSuppressionTarget(getProject());
    if (target instanceof PsiClass psiClass) {
      assertEquals(className, psiClass.getName());
    }
    else {
      Assertions.fail("Expected target to be of PsiClass type but got " + (target == null ? "null" : target.getClass().getName()));
    }
  }
}
