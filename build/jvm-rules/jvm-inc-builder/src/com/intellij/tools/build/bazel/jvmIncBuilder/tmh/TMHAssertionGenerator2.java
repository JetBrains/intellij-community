// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.IOException;
import java.util.Set;

@ApiStatus.Internal
public final class TMHAssertionGenerator2 implements TMHAssertionGenerator {

  private static final String THREAD_ASSERTIONS_CLASS_NAME = "com/intellij/util/concurrency/ThreadingAssertions";
  private static final String GENERATE_ASSERTION_PARAMETER = "generateAssertion";
  private static final String ASSERTION_METHOD_DESCRIPTOR = "()V";
  private static final String SUSPEND_METHOD_DESCRIPTOR_SUFFIX = "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;";

  private final String myThreadAssertionsClassName;
  private final Type myAnnotationClass;
  private final String myAssertionMethodName;

  TMHAssertionGenerator2(String threadAssertionsClassName, Type annotationClass, String assertionMethodName) {
    myThreadAssertionsClassName = threadAssertionsClassName;
    myAnnotationClass = annotationClass;
    myAssertionMethodName = assertionMethodName;
  }

  @Override
  public boolean isMyAnnotation(String annotationDescriptor) {
    return myAnnotationClass.getDescriptor().equals(annotationDescriptor);
  }

  /**
   * A resumption of a suspend function re-enters the method on the resuming thread.
   * An injected check would run again on each resumption, so skip a suspend function.
   */
  @Override
  public boolean isApplicableMethod(String methodDescriptor) {
    return !methodDescriptor.endsWith(SUSPEND_METHOD_DESCRIPTOR_SUFFIX);
  }

  public boolean hasAssertionMethod(@NotNull InstrumentationClassFinder finder) {
    try {
      return finder.loadClass(myThreadAssertionsClassName).findMethod(myAssertionMethodName, ASSERTION_METHOD_DESCRIPTOR) != null;
    }
    catch (IOException | ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public AnnotationVisitor getAnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
    return new AnnotationChecker(api, onShouldGenerateAssertion);
  }

  @Override
  public void generateAssertion(MethodVisitor writer, int methodStartLineNumber) {
    if (methodStartLineNumber != -1) {
      Label generatedCodeStart = new Label();
      writer.visitLabel(generatedCodeStart);
      writer.visitLineNumber(methodStartLineNumber, generatedCodeStart);
    }
    writer.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      myThreadAssertionsClassName,
      myAssertionMethodName,
      ASSERTION_METHOD_DESCRIPTOR,
      false
    );
  }

  static class AnnotationChecker extends AnnotationVisitor {
    private boolean myShouldGenerateAssertion = true;
    private final Runnable myOnShouldGenerateAssertion;

    private AnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
      super(api);
      myOnShouldGenerateAssertion = onShouldGenerateAssertion;
    }

    @Override
    public void visit(String annotationParameterName, Object value) {
      if (GENERATE_ASSERTION_PARAMETER.equals(annotationParameterName) && Boolean.FALSE.equals(value)) {
        myShouldGenerateAssertion = false;
      }
    }

    @Override
    public void visitEnd() {
      if (myShouldGenerateAssertion) {
        myOnShouldGenerateAssertion.run();
      }
    }
  }

  // TODO avoid hardcoding annotation names
  public static @NotNull Set<TMHAssertionGenerator2> generators(@NotNull OutputOrigin.Kind originKind) {
    return originKind == OutputOrigin.Kind.kotlin ? GENERATORS_KOTLIN : GENERATORS_JAVA;

  }

  private static final Set<TMHAssertionGenerator2> GENERATORS_JAVA = generators(
    THREAD_ASSERTIONS_CLASS_NAME,
    "com/intellij/util/concurrency/annotations",
    OutputOrigin.Kind.java
  );

  private static final Set<TMHAssertionGenerator2> GENERATORS_KOTLIN = generators(
    THREAD_ASSERTIONS_CLASS_NAME,
    "com/intellij/util/concurrency/annotations",
    OutputOrigin.Kind.kotlin
  );

  public static @NotNull Set<TMHAssertionGenerator2> generators(
    @NotNull String threadAssertionsClassName,
    @NotNull String packageString
  ) {
    return generators(threadAssertionsClassName, packageString, OutputOrigin.Kind.java);
  }

  public static @NotNull Set<TMHAssertionGenerator2> generators(
    @NotNull String threadAssertionsClassName,
    @NotNull String packageString,
    @NotNull OutputOrigin.Kind originKind
  ) {
    if (originKind == OutputOrigin.Kind.kotlin) {
      return Set.of(
        generator(threadAssertionsClassName, packageString + "/RequiresEdt", "softAssertEventDispatchThread"),
        generator(threadAssertionsClassName, packageString + "/RequiresBackgroundThread", "softAssertBackgroundThread"),
        generator(threadAssertionsClassName, packageString + "/RequiresReadLock", "softAssertReadAccess"),
        generator(threadAssertionsClassName, packageString + "/RequiresReadLockAbsence", "softAssertNoReadAccess"),
        generator(threadAssertionsClassName, packageString + "/RequiresWriteLock", "softAssertWriteAccess")
      );
    } else {
      return Set.of(
        generator(threadAssertionsClassName, packageString + "/RequiresEdt", "assertEventDispatchThread"),
        generator(threadAssertionsClassName, packageString + "/RequiresBackgroundThread", "assertBackgroundThread"),
        generator(threadAssertionsClassName, packageString + "/RequiresReadLock", "softAssertReadAccess"),
        generator(threadAssertionsClassName, packageString + "/RequiresReadLockAbsence", "assertNoReadAccess"),
        generator(threadAssertionsClassName, packageString + "/RequiresWriteLock", "assertWriteAccess")
      );
    }
  }

  private static @NotNull TMHAssertionGenerator2 generator(
    @NotNull String threadAssertionsClassName,
    @NotNull String annotationClassName,
    @NotNull String assertionMethodName
  ) {
    return new TMHAssertionGenerator2(threadAssertionsClassName, Type.getType("L" + annotationClassName + ";"), assertionMethodName);
  }
}
