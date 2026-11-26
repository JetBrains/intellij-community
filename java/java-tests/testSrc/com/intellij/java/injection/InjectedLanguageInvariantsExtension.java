// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.injection;

import com.intellij.openapi.editor.impl.RedBlackTree;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.TestFrameworkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;

/**
 * Runs each test method, and each {@code @BeforeEach} and {@code @AfterEach} method of the test class, with the internal invariant checks
 * of the range markers ({@link RedBlackTree#runAssertingInternalInvariants}) and of the PSI ({@link DebugUtil#runWithCheckInternalInvariantsEnabled}).
 * <p>
 * A stress test, as {@link TestFrameworkUtil#isStressTest} decides it, and a test with {@code NoVerify} in its name run without the checks.
 * The setup and the teardown of the fixtures run without the checks.
 * Put it on the class with {@code @ExtendWith(InjectedLanguageInvariantsExtension.class)}, next to {@link InjectedLanguageFixtureKt#injectedLanguageFixture}.
 */
@TestOnly
public final class InjectedLanguageInvariantsExtension implements InvocationInterceptor {
  @Override
  public void interceptBeforeEachMethod(@NotNull Invocation<Void> invocation,
                                        @NotNull ReflectiveInvocationContext<Method> invocationContext,
                                        @NotNull ExtensionContext extensionContext) throws Throwable {
    proceedWithChecks(invocation, extensionContext);
  }

  @Override
  public void interceptTestMethod(@NotNull Invocation<Void> invocation,
                                  @NotNull ReflectiveInvocationContext<Method> invocationContext,
                                  @NotNull ExtensionContext extensionContext) throws Throwable {
    proceedWithChecks(invocation, extensionContext);
  }

  @Override
  public void interceptAfterEachMethod(@NotNull Invocation<Void> invocation,
                                       @NotNull ReflectiveInvocationContext<Method> invocationContext,
                                       @NotNull ExtensionContext extensionContext) throws Throwable {
    proceedWithChecks(invocation, extensionContext);
  }

  private static void proceedWithChecks(@NotNull Invocation<Void> invocation, @NotNull ExtensionContext extensionContext) throws Throwable {
    String testName = extensionContext.getRequiredTestMethod().getName();
    boolean mustCheck = !testName.contains("NoVerify") && !TestFrameworkUtil.isStressTest(testName, extensionContext.getRequiredTestClass());
    if (mustCheck) {
      RedBlackTree.runAssertingInternalInvariants(() -> DebugUtil.runWithCheckInternalInvariantsEnabled(() -> invocation.proceed()));
    }
    else {
      invocation.proceed();
    }
  }
}
