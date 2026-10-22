// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
 * Instruments the content of one class, as a BytecodeInstrumenter of the jvm-inc-builder worker does it for the Java
 * output. NotNullInstrumenter and TMHInstrumenter each apply one instrumentation. CompositeInstrumenter runs a list of
 * instrumenters one after the other.
 */
interface ClassInstrumenter {
  String getName();

  /**
   * @return the instrumented content, or null when the instrumenter does not change the class
   */
  byte @Nullable [] instrument(byte[] content, InstrumentationClassFinder finder);

  /**
   * The class writer that BytecodeInstrumentationRunner gives to each instrumenter.
   */
  static ClassWriter newWriter(ClassReader reader, InstrumentationClassFinder finder) {
    int version = InstrumenterClassWriter.getClassFileVersion(reader);
    return new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder);
  }

}
