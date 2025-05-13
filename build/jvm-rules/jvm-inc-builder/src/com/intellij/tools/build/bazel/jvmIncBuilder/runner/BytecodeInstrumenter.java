// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.runner;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

public interface BytecodeInstrumenter extends Runner{
  /**
   * @return null, if instrumentation did not happen, otherwise an instrumented content
   */
  byte @Nullable [] instrument(String filePath, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) throws Exception;
}
