// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CastMethodParametersOrderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  /**
   * Verifies that 'cast argument to' fixes, which generate code that contains further compilation errors,
   * are placed at the bottom of the quick-fix list.
   */
  @Test
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
  @Test
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
  @Test
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

  @Test
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
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("AClass.java", code);
    var actionNames = ContainerUtil.map(daemon.getAvailableActions(), a -> a.getText());
    assertThat(actionNames).containsSubsequence(expectedQuickFixes);
  }
}
