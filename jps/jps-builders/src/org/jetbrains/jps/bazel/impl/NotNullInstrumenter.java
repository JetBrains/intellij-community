// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.runner.BytecodeInstrumenter;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

public class NotNullInstrumenter implements BytecodeInstrumenter {
  @Override
  public String getName() {
    return "NotNull Instrumenter";
  }

  @Override
  public byte @Nullable [] instrument(String filePath, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) throws Exception {
    return new byte[0]; // todo
  }

}
