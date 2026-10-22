// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the worker's instrumenters for the Java output on compiled classes, as BytecodeInstrumentationRunner does: in its
 * order and with a new InstrumenterClassWriter for each instrumenter. The class finder is set up as in StorageManager:
 * the platform of the running JDK, then the output, then the class path.
 */
public final class WorkerJavaInstrumentation {
  private WorkerJavaInstrumentation() {
  }

  /**
   * @param classes the class files by their path in the output directory, for example {@code a/B.class}
   * @return the class files after the instrumentation, by the same paths
   */
  public static Map<String, byte[]> instrument(Map<String, byte[]> classes, List<Path> classPath) throws Exception {
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
    List<BytecodeInstrumenter> instrumenters = List.of(new ThreadingModelInstrumenter(OutputOrigin.Kind.java), new NotNullInstrumenter());
    try {
      for (String path : classes.keySet()) {
        byte[] content = output.get(path);
        boolean changes = false;
        ClassReader reader = null;
        for (BytecodeInstrumenter instrumenter : instrumenters) {
          if (reader == null) {
            reader = new FailSafeClassReader(content);
          }
          int version = InstrumenterClassWriter.getClassFileVersion(reader);
          ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder);
          byte[] instrumented = instrumenter.instrument(path, reader, writer, finder);
          if (instrumented != null) {
            changes = true;
            content = instrumented;
            finder.cleanCachedData(reader.getClassName());
            reader = null;
          }
        }
        if (changes) {
          output.put(path, content);
        }
      }
    }
    finally {
      finder.releaseResources();
    }
    return output;
  }
}
