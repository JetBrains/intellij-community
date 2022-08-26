// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.agent;

import java.lang.instrument.Instrumentation;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class DebuggerAgent {
  public static void premain(String args, Instrumentation instrumentation) {
    // never instrument twice
    if (System.getProperty("intellij.debug.agent") != null) {
      System.err.println("Debugger agent: more than one agent is not allowed, skipping");
      return;
    }
    System.setProperty("intellij.debug.agent", "true");

    CaptureAgent.init(args, instrumentation);
    CollectionBreakpointInstrumentor.init(instrumentation);
  }
}
