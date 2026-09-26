// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHTestUtil;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThreadingModelInstrumenterTest {
  private static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";

  @Test
  public void testInstrumentsWithThreadingAssertions() throws Exception {
    assertInstrumented(true, "assertEventDispatchThread");
  }

  @Test
  public void testInstrumentsWithoutThreadingAssertions() throws Exception {
    assertInstrumented(false, "assertIsDispatchThread");
  }

  private static void assertInstrumented(boolean hasThreadingAssertions, String assertionMethod) throws Exception {
    Path classesDir = compileFixtures(hasThreadingAssertions);
    byte[] classData = Files.readAllBytes(classesDir.resolve("test/Sample.class"));
    String oldValue = System.getProperty(INSTRUMENT_ANNOTATIONS_PROPERTY);
    System.setProperty(INSTRUMENT_ANNOTATIONS_PROPERTY, "true");
    try {
      ClassReader reader = new ClassReader(classData);
      ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
      InstrumentationClassFinder finder = new InstrumentationClassFinder(new URL[]{classesDir.toUri().toURL()});
      byte[] instrumented = new ThreadingModelInstrumenter(OutputOrigin.Kind.java).instrument("Sample.class", reader, writer, finder);

      assertNotNull(instrumented);
      assertTrue(TMHTestUtil.containsMethodCall(instrumented, assertionMethod));
    }
    finally {
      if (oldValue == null) {
        System.clearProperty(INSTRUMENT_ANNOTATIONS_PROPERTY);
      }
      else {
        System.setProperty(INSTRUMENT_ANNOTATIONS_PROPERTY, oldValue);
      }
    }
  }

  private static Path compileFixtures(boolean hasThreadingAssertions) throws Exception {
    Path classesDir = Files.createTempDirectory("tmh-instrumenter");
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull("The test needs a JDK compiler", compiler);
    List<SimpleJavaFileObject> sources = new java.util.ArrayList<>();
    sources.add(source("com.intellij.util.concurrency.annotations.RequiresEdt", """
      package com.intellij.util.concurrency.annotations;
      public @interface RequiresEdt {}
      """));
    sources.add(source("test.Sample", """
      package test;
      import com.intellij.util.concurrency.annotations.RequiresEdt;
      public class Sample {
        @RequiresEdt public void run() {}
      }
      """));
    if (hasThreadingAssertions) {
      sources.add(source("com.intellij.util.concurrency.ThreadingAssertions", """
        package com.intellij.util.concurrency;
        public final class ThreadingAssertions {
          public static void assertEventDispatchThread() {}
        }
        """));
    }
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
      fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classesDir));
      assertTrue("Cannot compile test sources", compiler.getTask(null, fileManager, null, null, null, sources).call());
    }
    return classesDir;
  }

  private static SimpleJavaFileObject source(String className, String source) {
    return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"), SimpleJavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return source;
      }
    };
  }
}