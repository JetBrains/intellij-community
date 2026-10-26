// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHInstrumenter;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The reference output: the TMHInstrumenter of instrumentation-core on the class files of a compile without the plugin,
 * as the jvm-inc-builder worker ran it for the Kotlin output before the plugin took that over. Each class gets a new
 * InstrumenterClassWriter. The class finder has the search order of the worker: the platform of the running JDK, then
 * the output, then the classpath.
 */
final class ReferenceInstrumentation {
  private static final String ASSERTIONS_CLASS = "com/intellij/util/concurrency/ThreadingAssertions";

  private ReferenceInstrumentation() {
  }

  /**
   * @param classes the class files by their path in the output directory, for example {@code a/B.class}
   * @param generateLineNumbers true gives each assertion call the first line number of its method
   * @return the class files after the instrumentation, by the same paths
   */
  static Map<String, byte[]> instrument(Map<String, byte[]> classes, List<Path> classPath, boolean generateLineNumbers) throws Exception {
    Map<String, byte[]> output = new TreeMap<>(classes);
    URL[] platform = {InstrumentationClassFinder.createJDKPlatformUrl(System.getProperty("java.home"))};
    URL[] classPathUrls = new URL[classPath.size()];
    for (int i = 0; i < classPathUrls.length; i++) {
      classPathUrls[i] = classPath.get(i).toUri().toURL();
    }
    InstrumentationClassFinder finder = new InstrumentationClassFinder(platform, classPathUrls) {
      @Override
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        byte[] content = output.get(internalClassName + ".class");
        return content != null ? new ByteArrayInputStream(content) : null;
      }
    };
    try {
      Set<TMHAssertionGenerator2> generators = generators(finder);
      if (generators.isEmpty()) {
        return output;
      }
      for (String path : classes.keySet()) {
        ClassReader reader = new FailSafeClassReader(output.get(path));
        int version = InstrumenterClassWriter.getClassFileVersion(reader);
        ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder);
        if (TMHInstrumenter.instrument(reader, writer, generators, generateLineNumbers)) {
          output.put(path, writer.toByteArray());
          finder.cleanCachedData(reader.getClassName());
        }
      }
    }
    finally {
      finder.releaseResources();
    }
    return output;
  }

  /**
   * The generators for the Kotlin output: the soft assertion methods of ThreadingAssertions that exist, or none when the
   * class finder has no ThreadingAssertions.
   */
  private static Set<TMHAssertionGenerator2> generators(InstrumentationClassFinder finder) {
    try {
      finder.loadClass(ASSERTIONS_CLASS);
    }
    catch (IOException | ClassNotFoundException e) {
      return Set.of();
    }
    return TMHAssertionGenerator2.generators(TMHAssertionGenerator2.OriginKind.KOTLIN)
      .stream()
      .filter(generator -> generator.hasAssertionMethod(finder))
      .collect(Collectors.toSet());
  }
}
