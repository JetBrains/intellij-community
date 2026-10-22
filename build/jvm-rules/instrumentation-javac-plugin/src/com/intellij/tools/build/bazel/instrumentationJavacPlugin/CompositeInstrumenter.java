// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Runs a list of instrumenters over the content of a class, as the instrumenter loop of BytecodeInstrumentationRunner
 * does. Each instrumenter gets the content that the instrumenters before it made. The first failure ends the loop.
 * Unlike BytecodeInstrumentationRunner, this loop does not drop the cached data of the class from the class finder.
 * {@link InstrumentationTaskListener} drops it after the write.
 */
final class CompositeInstrumenter implements ClassInstrumenter {
  private final List<ClassInstrumenter> myInstrumenters;

  CompositeInstrumenter(List<ClassInstrumenter> instrumenters) {
    myInstrumenters = instrumenters;
  }

  @Override
  public String getName() {
    return "Bytecode Instrumentation";
  }

  @Override
  public byte @Nullable [] instrument(byte[] content, InstrumentationClassFinder finder) {
    byte[] current = content;
    boolean changed = false;
    for (ClassInstrumenter instrumenter : myInstrumenters) {
      try {
        byte[] instrumented = instrumenter.instrument(current, finder);
        if (instrumented != null) {
          changed = true;
          current = instrumented;
        }
      }
      catch (Throwable e) {
        String message = e.getMessage();
        throw new RuntimeException(instrumenter.getName() + ": " + (message != null ? message : e.toString()), e);
      }
    }
    return changed ? current : null;
  }
}
