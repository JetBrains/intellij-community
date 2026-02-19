// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.EnumSet;
import java.util.Set;

class NotNullInstrumenter implements BytecodeInstrumenter {
  // todo: make it configurable?
  private static final String[] ourNotNulls = {
    "org.jetbrains.annotations.NotNull"
  };

  @Override
  public String getName() {
    return "NotNull Instrumenter";
  }

  @Override
  public Set<OutputOrigin.Kind> getSupportedOrigins() {
    return EnumSet.of(OutputOrigin.Kind.java);
  }

  @Override
  public byte @Nullable [] instrument(String filePath, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) {
    if ((InstrumenterClassWriter.getClassFileVersion(reader) & 0xFFFF) >= Opcodes.V1_5 && !"module-info".equals(reader.getClassName())) {
      // perform instrument
      if (NotNullVerifyingInstrumenter.processClassFile(reader, writer, ourNotNulls)) {
        return writer.toByteArray();
      }
    }
    
    return null;
  }

}
