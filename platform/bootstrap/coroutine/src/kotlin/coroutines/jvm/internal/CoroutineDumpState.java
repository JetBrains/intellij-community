// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package kotlin.coroutines.jvm.internal;

// cannot be in DebugProbesKt - in some context our override of class DebugProbesKt maybe not loaded
@SuppressWarnings({"KotlinInternalInJava", "UnnecessaryFullyQualifiedName", "RedundantSuppression"})
public final class CoroutineDumpState {
  // not volatile - that's ok to miss something
  static boolean INSTALLED = false;

  public static void install() {
    if (INSTALLED) {
      return;
    }

    // set to true - otherwise, install will try to load byte-buddy
    kotlinx.coroutines.debug.internal.AgentInstallationType.INSTANCE.setInstalledStatically$kotlinx_coroutines_core(true);
    kotlinx.coroutines.debug.internal.DebugProbesImpl.INSTANCE.install$kotlinx_coroutines_core();

    INSTALLED = true;
  }
}