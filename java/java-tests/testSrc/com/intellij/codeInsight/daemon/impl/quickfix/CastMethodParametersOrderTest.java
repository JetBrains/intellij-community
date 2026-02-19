// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class CastMethodParametersOrderTest extends LightQuickFixTestCase {

  /**
   * Verifies that 'cast argument to' fixes, which generate code that contains further compilation errors,
   * are placed at the bottom of the quick-fix list.
   */
  public void testOrderOfCastMethodArgumentQuickFixesForLambdaArgument() {
    String code = """
      import java.io.IOException;
      import java.util.concurrent.Callable;
      import java.util.function.Supplier;
      public class AClass {
          void foo1() {
              m1(<caret>() -> { throw new IOException(); });
          }
          void m1(Supplier<String> a) {}
          void m1(Callable<String> a) {}
      }
      """;
    var expectedQuickFixes = List.of(
      "Cast argument to 'Callable<String>'",
      "Cast argument to 'Supplier<String>'");
    assertThatFollowingQuickFixesAreAvailableInOrder(code, expectedQuickFixes);
  }

  /**
   * Verifies that 'cast argument to' fixes, which generate code that contains further compilation errors,
   * are placed at the bottom of the quick-fix list.
   */
  public void testOrderOfCastMethodArgumentQuickFixesForMethodReferenceArgument() {
    String code = """
      import java.io.IOException;
      import java.util.concurrent.Callable;
      import java.util.function.Supplier;
      public class AClass {
        void foo2() {
            m1(<caret>this::returnString);
        }
        public String returnString() throws IOException {
            return null;
        }
        void m1(Supplier<String> a) {}
        void m1(Callable<String> a) {}
      }
      """;
    List<String> expectedQuickFixes = List.of(
      "Cast argument to 'Callable<String>'",
      "Cast argument to 'Supplier<String>'");
    assertThatFollowingQuickFixesAreAvailableInOrder(code, expectedQuickFixes);
  }

  /**
   * Verifies that 'cast argument to' fixes, which generate code that contains further compilation errors,
   * are placed at the bottom of the quick-fix list.
   */
  public void testOrderOfCastMethodArgumentQuickFixesForParameterWithGenericThrow() {
    String code = """
      import java.io.IOException;
      import java.util.concurrent.Callable;
      import java.util.function.Supplier;
      public class AClass {
          void foo1() {
              m1(<caret>() -> { throw new IOException(); });
          }
          void m1(Supplier<String> a) {}
          void m1(Callable<String> a) {}
          <E extends Throwable> void m1(ThrowingRunnable<E> a) {}
      }
      interface ThrowingRunnable<T extends Throwable> {
          void run() throws T;
      }
      """;
    List<String> expectedQuickFixes = List.of(
      "Cast argument to 'Callable<String>'",
      "Cast argument to 'ThrowingRunnable<IOException>'",
      "Cast argument to 'Supplier<String>'");
    assertThatFollowingQuickFixesAreAvailableInOrder(code, expectedQuickFixes);
  }

  public void testOrderOfCastMethodArgumentQuickFixesForParameterWithRawGenericThrow() {
    String code = """
      import java.io.IOException;
      import java.util.concurrent.Callable;
      import java.util.function.Supplier;
      public class AClass {
          void foo1() {
              m1(<caret>() -> { throw new IOException(); });
          }
          void m1(Supplier<String> a) {}
          void m1(Callable<String> a) {}
          <E extends Throwable> void m1(ThrowingRunnable a) {}
      }
      interface ThrowingRunnable<T extends Throwable> {
          void run() throws T;
      }
      """;
    List<String> expectedQuickFixes = List.of(
      "Cast argument to 'Callable<String>'",
      "Cast argument to 'ThrowingRunnable'",
      "Cast argument to 'Supplier<String>'");
    assertThatFollowingQuickFixesAreAvailableInOrder(code, expectedQuickFixes);
  }

  private void assertThatFollowingQuickFixesAreAvailableInOrder(String code, List<@NotNull String> expectedQuickFixes) {
    configureFromFileText("AClass.java", code);
    var actionNames = ContainerUtil.map(getAvailableActions(), a -> a.getText());
    assertThat(actionNames).containsSubsequence(expectedQuickFixes);
  }
}
