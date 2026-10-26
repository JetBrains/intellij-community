// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator1;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The threading model instrumentation of the Java output. It keeps the existing assertion methods, see IJPL-115548.
 * The Kotlin output gets its assertions from the tmh-assertions-kotlin-plugin inside the compiler, on both backends.
 */
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
    return EnumSet.of(OutputOrigin.Kind.java);
  }

  @Override
  public byte @Nullable [] instrument(String filePath, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) {
    if (myIsEnabled && !"module-info".equals(reader.getClassName())) {
      var generators = getGenerators(finder);
      if (TMHInstrumenter.instrument(reader, writer, generators, myIsGenerateLineNumbers)) {
        return writer.toByteArray();
      }
    }

    return null;
  }

  /**
   * Returns generators whose assertion methods are available in ThreadingAssertions.
   * This provides backward compatability by avoiding generating calls to methods that do not exist
   */
  private static Set<? extends TMHAssertionGenerator> getGenerators(InstrumentationClassFinder finder) {
    if (hasThreadingAssertions(finder)) {
      return TMHAssertionGenerator2.generators(TMHAssertionGenerator2.OriginKind.JAVA)
        .stream()
        .filter(generator -> generator.hasAssertionMethod(finder))
        .collect(Collectors.toSet());
    }
    return TMHAssertionGenerator1.generators();
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
