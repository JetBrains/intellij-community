// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;

@ApiStatus.Internal
public interface TMHAssertionGenerator {
  boolean isMyAnnotation(String annotationDescriptor);

  AnnotationVisitor getAnnotationChecker(int api, Runnable onShouldGenerateAssertion);

  void generateAssertion(MethodVisitor writer, int methodStartLineNumber);
}
