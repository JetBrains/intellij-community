// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.LightOrRealThreadInfo;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;

/**
 * Utility class for providing an implicit evaluation-ready {@link LightOrRealThreadInfo} for a particular {@link DebugProcess}.
 * This might be useful to support evaluations in the current {@link com.intellij.debugger.impl.DebuggerSession},
 * but keeping any UI elements hidden.
 * <p>
 * {@link DebugProcessImpl#isEvaluationPossible()}
 */
@ApiStatus.Experimental
public final class DebuggerImplicitEvaluationContextUtil {
  private static final Key<LightOrRealThreadInfo> IMPLICIT_EVALUATION_READY_THREAD_KEY = new Key<>("ImplicitEvaluationThread");

  public static LightOrRealThreadInfo getImplicitEvaluationThread(DebugProcess process) {
    return process.getUserData(IMPLICIT_EVALUATION_READY_THREAD_KEY);
  }

  public static void provideImplicitEvaluationThread(DebugProcess process, LightOrRealThreadInfo thread) {
    process.putUserData(IMPLICIT_EVALUATION_READY_THREAD_KEY, thread);
  }
}
