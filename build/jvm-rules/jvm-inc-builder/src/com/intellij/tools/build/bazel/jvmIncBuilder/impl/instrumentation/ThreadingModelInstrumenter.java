// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator1;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

public class ThreadingModelInstrumenter implements BytecodeInstrumenter {
  public static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";
  public static final String GENERATE_LINE_NUMBERS_PROPERTY = "tmh.generate.line.numbers";
  
  private static final String ASSERTIONS_CLASS = "com/intellij/util/concurrency/ThreadingAssertions";
  private final boolean myIsEnabled;
  private final boolean myIsGenerateLineNumbers;

  ThreadingModelInstrumenter() {
    myIsEnabled = Boolean.getBoolean(INSTRUMENT_ANNOTATIONS_PROPERTY);
    myIsGenerateLineNumbers = Boolean.getBoolean(GENERATE_LINE_NUMBERS_PROPERTY);
  }

  @Override
  public String getName() {
    return "Threading Model instrumentation";
  }

  @Override
  public Set<OutputOrigin.Kind> getSupportedOrigins() {
    // todo: Instrumentation of kotlinc-produced code is turned off to comply with the current JPS behavior.
    // todo: Uncomment as soon as 'kotlin.jps.instrument.bytecode' flag is set to 'true'
    return EnumSet.of(OutputOrigin.Kind.java/*, OutputOrigin.Kind.kotlin*/);
  }

  @Override
  public byte @Nullable [] instrument(String filePath, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) {
    if (myIsEnabled && !"module-info".equals(reader.getClassName())) {
      var generators = hasThreadingAssertions(finder) ? TMHAssertionGenerator2.generators()
                                                      : TMHAssertionGenerator1.generators();
      if (TMHInstrumenter.instrument(reader, writer, generators, myIsGenerateLineNumbers)) {
        return writer.toByteArray();
      }
    }

    return null;
  }

  private static boolean hasThreadingAssertions(InstrumentationClassFinder finder) {
    try {
      finder.loadClass(ASSERTIONS_CLASS);
      return true;
    }
    catch (IOException | ClassNotFoundException e) {
      return false;
    }
  }
}
