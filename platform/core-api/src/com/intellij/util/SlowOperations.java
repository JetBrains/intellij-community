// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class SlowOperations {

  private static final Logger LOG = Logger.getInstance(SlowOperations.class);
  private static final Set<String> ourReportedTraces = new HashSet<>();
  private static final String[] misbehavingFrames = {
    "org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler",
    "org.jetbrains.kotlin.idea.actions.KotlinAddImportAction",
    "org.jetbrains.kotlin.idea.codeInsight.KotlinCopyPasteReferenceProcessor",
    "com.intellij.apiwatcher.plugin.presentation.bytecode.UsageHighlighter",
  };
  private static boolean ourAllowedFlag = false;

  private SlowOperations() {}

  /**
   * If you get an exception from this method, then you need to move the computation to the background
   * while also trying to avoid blocking the UI thread as well. It's okay if the API changes in the process,
   * e.g. instead of wrapping implementation of some extension into {@link #allowSlowOperations},
   * it's better to admit that the EP semantic as a whole requires index access,
   * and then move the iteration over all extensions to the background on the platform-side.
   * <p/>
   * In cases when it's impossible to do so, the computation should be wrapped into {@code allowSlowOperations} explicitly.
   * This way it's possible to find all such operations by searching usages of {@code allowSlowOperations}.
   *
   * @see com.intellij.openapi.application.NonBlockingReadAction
   * @see com.intellij.openapi.application.CoroutinesKt#readAction
   * @see com.intellij.openapi.actionSystem.ex.ActionUtil#underModalProgress
   */
  public static void assertSlowOperationsAreAllowed() {
    if (Registry.is("ide.enable.slow.operations.in.edt")) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || !application.isDispatchThread() || ourAllowedFlag) {
      return;
    }
    String stackTrace = ExceptionUtil.currentStackTrace();
    if (ContainerUtil.or(misbehavingFrames, stackTrace::contains) || !ourReportedTraces.add(stackTrace)) {
      return;
    }
    LOG.error("Slow operations are prohibited in the EDT");
  }

  public static <T, E extends Throwable> T allowSlowOperations(@NotNull ThrowableComputable<T, E> computable) throws E {
    if (!ApplicationManager.getApplication().isDispatchThread() || ourAllowedFlag) {
      return computable.compute();
    }
    ourAllowedFlag = true;
    try {
      return computable.compute();
    }
    finally {
      ourAllowedFlag = false;
    }
  }

  public static <E extends Throwable> void allowSlowOperations(@NotNull ThrowableRunnable<E> runnable) throws E {
    allowSlowOperations(() -> {
      runnable.run();
      return null;
    });
  }
}
