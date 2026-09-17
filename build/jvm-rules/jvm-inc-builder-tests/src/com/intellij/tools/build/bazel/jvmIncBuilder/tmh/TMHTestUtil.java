// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TMHTestUtil {
  private static final String REQUIRES_EDT_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresEdt";
  private static final String REQUIRES_BACKGROUND_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresBackgroundThread";
  private static final String REQUIRES_READ_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresReadLock";
  private static final String REQUIRES_WRITE_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresWriteLock";
  private static final String REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME =
    "com/intellij/util/concurrency/annotations/fake/RequiresReadLockAbsence";
  private static final String APPLICATION_MANAGER_CLASS_NAME = "com/intellij/openapi/application/fake/ApplicationManager";
  private static final String APPLICATION_CLASS_NAME = "com/intellij/openapi/application/fake/Application";

  public static void printDebugInfo(byte[] classData, byte[] instrumentedClassData) {
    System.out.println(classDataToText(classData));
    System.out.println();
    System.out.println(classDataToText(instrumentedClassData));
  }

  public static String classDataToText(byte[] data) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    @SuppressWarnings("ImplicitDefaultCharsetUsage")
    PrintWriter printWriter = new PrintWriter(buffer);
    TraceClassVisitor visitor = new TraceClassVisitor(printWriter);
    new ClassReader(data).accept(visitor, 0);
    return buffer.toString();
  }

  public static boolean containsMethodCall(byte[] classBytes, final String methodName) {
    boolean[] contains = {false};
    ClassVisitor visitor = new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access,
                                       String name,
                                       String descriptor,
                                       String signature,
                                       String[] exceptions) {
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals(methodName)) {
              contains[0] = true;
            }
          }
        };
      }
    };
    ClassReader reader = new ClassReader(classBytes);
    reader.accept(visitor, 0);
    return contains[0];
  }

  public static List<Integer> getLineNumbers(byte[] classBytes) {
    List<Integer> lineNumbers = new ArrayList<>();
    ClassVisitor visitor = new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access,
                                       String name,
                                       String descriptor,
                                       String signature,
                                       String[] exceptions) {
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitLineNumber(int line, Label start) {
            lineNumbers.add(line);
          }
        };
      }
    };
    ClassReader reader = new ClassReader(classBytes);
    reader.accept(visitor, 0);
    return lineNumbers;
  }

  public static byte[] instrument(byte[] classData, boolean useThreadingAssertions) {
    FailSafeClassReader reader = new FailSafeClassReader(classData);
    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

    var generators = useThreadingAssertions ? TMHAssertionGenerator2.generators(
      "com/intellij/util/concurrency/fake/ThreadingAssertions",
      "com/intellij/util/concurrency/annotations/fake"
    ) : Set.of(
      new TMHAssertionGenerator1.AssertEdt(REQUIRES_EDT_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator1.AssertBackgroundThread(REQUIRES_BACKGROUND_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator1.AssertReadAccess(REQUIRES_READ_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator1.AssertWriteAccess(REQUIRES_WRITE_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator1.AssertNoReadAccess(REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME)
    );
    boolean instrumented = TMHInstrumenter.instrument(reader, writer, generators, true);
    return instrumented ? writer.toByteArray() : null;
  }
}
