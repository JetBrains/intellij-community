// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator1;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The threading model instrumentation for the Java output, with the conditions of ThreadingModelInstrumenter in the
 * jvm-inc-builder worker. It runs only when the system property tmh.instrument.annotations is true. The bytecode change
 * itself comes from the TMHInstrumenter of instrumentation-core, which has the same simple name.
 */
final class TMHInstrumenter implements ClassInstrumenter {
  private static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";
  private static final String GENERATE_LINE_NUMBERS_PROPERTY = "tmh.generate.line.numbers";
  private static final String ASSERTIONS_CLASS = "com/intellij/util/concurrency/ThreadingAssertions";

  private final boolean myIsEnabled;
  private final boolean myIsGenerateLineNumbers;

  TMHInstrumenter() {
    myIsEnabled = Boolean.getBoolean(INSTRUMENT_ANNOTATIONS_PROPERTY);
    myIsGenerateLineNumbers = Boolean.getBoolean(GENERATE_LINE_NUMBERS_PROPERTY);
  }

  @Override
  public String getName() {
    return "Threading Model instrumentation";
  }

  @Override
  public byte @Nullable [] instrument(byte[] content, InstrumentationClassFinder finder) {
    if (!myIsEnabled) {
      return null;
    }
    ClassReader reader = new FailSafeClassReader(content);
    if ("module-info".equals(reader.getClassName())) {
      return null;
    }
    ClassWriter writer = ClassInstrumenter.newWriter(reader, finder);
    boolean changed = com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter.instrument(
      reader, writer, generators(finder), myIsGenerateLineNumbers
    );
    return changed ? writer.toByteArray() : null;
  }

  /**
   * The generators for the Java output: the assertion methods of ThreadingAssertions that exist, else the assertions of
   * the older platform API.
   */
  private static Set<? extends TMHAssertionGenerator> generators(InstrumentationClassFinder finder) {
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
