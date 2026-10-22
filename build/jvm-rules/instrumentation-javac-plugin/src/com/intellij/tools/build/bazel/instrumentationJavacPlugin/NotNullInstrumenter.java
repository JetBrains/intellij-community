// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.notNullVerification.NotNullVerifyingInstrumenter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

/**
 * The NotNull instrumentation, with the conditions of NotNullInstrumenter in the jvm-inc-builder worker.
 */
final class NotNullInstrumenter implements ClassInstrumenter {
  private static final String[] NOT_NULL_ANNOTATIONS = {"org.jetbrains.annotations.NotNull"};

  @Override
  public String getName() {
    return "NotNull Instrumenter";
  }

  @Override
  public byte @Nullable [] instrument(byte[] content, InstrumentationClassFinder finder) {
    ClassReader reader = new FailSafeClassReader(content);
    if ((InstrumenterClassWriter.getClassFileVersion(reader) & 0xFFFF) < Opcodes.V1_5 || "module-info".equals(reader.getClassName())) {
      return null;
    }
    ClassWriter writer = ClassInstrumenter.newWriter(reader, finder);
    return NotNullVerifyingInstrumenter.processClassFile(reader, writer, NOT_NULL_ANNOTATIONS) ? writer.toByteArray() : null;
  }
}
