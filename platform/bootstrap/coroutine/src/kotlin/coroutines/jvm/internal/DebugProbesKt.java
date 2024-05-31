// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package kotlin.coroutines.jvm.internal;

import kotlin.coroutines.Continuation;

// API note: this is a substitute for the original class; ApiCheckTest considers it as part of the API due to `public` modifier,
//           but actually it's part of the library's API
@SuppressWarnings({"KotlinInternalInJava", "RedundantSuppression", "unchecked", "UnnecessaryFullyQualifiedName", "unused", "rawtypes"})
public final class DebugProbesKt {
  public static <T> Continuation<T> probeCoroutineCreated(Continuation completion) {
    if (CoroutineDumpState.INSTALLED) {
      return kotlinx.coroutines.debug.internal.DebugProbesImpl.INSTANCE.probeCoroutineCreated$kotlinx_coroutines_core(completion);
    }
    else {
      return completion;
    }
  }

  public static void probeCoroutineResumed(Continuation frame) {
    if (CoroutineDumpState.INSTALLED) {
      kotlinx.coroutines.debug.internal.DebugProbesImpl.INSTANCE.probeCoroutineResumed$kotlinx_coroutines_core(frame);
    }
  }

  public static void probeCoroutineSuspended(Continuation frame) {
    if (CoroutineDumpState.INSTALLED) {
      kotlinx.coroutines.debug.internal.DebugProbesImpl.INSTANCE.probeCoroutineSuspended$kotlinx_coroutines_core(frame);
    }
  }
}