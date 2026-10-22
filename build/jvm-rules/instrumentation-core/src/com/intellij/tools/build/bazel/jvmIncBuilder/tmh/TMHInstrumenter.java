// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeMethodVisitor;
import org.jetbrains.org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TMHInstrumenter {
  public static boolean instrument(ClassReader classReader,
                                   ClassVisitor classWriter,
                                   Set<? extends TMHAssertionGenerator> generators,
                                   boolean generateLineNumbers) {
    AnnotatedMethodsCollector collector = new AnnotatedMethodsCollector(generators);
    int options = ClassReader.SKIP_FRAMES;
    if (!generateLineNumbers) {
      options |= ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG;
    }
    classReader.accept(collector, options);
    if (collector.annotatedMethods.isEmpty()) {
      return false;
    }
    Instrumenter instrumenter = new Instrumenter(classWriter, collector.annotatedMethods);
    classReader.accept(instrumenter, 0);
    return true;
  }

  private static final class AnnotatedMethodsCollector extends ClassVisitor {
    final Set<? extends TMHAssertionGenerator> assertionGenerators;
    final Map<MethodKey, InstrumentationInfo> annotatedMethods = new HashMap<>();

    AnnotatedMethodsCollector(Set<? extends TMHAssertionGenerator> assertionGenerators) {
      super(Opcodes.API_VERSION);
      this.assertionGenerators = assertionGenerators;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String name, final String methodDescriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.API_VERSION) {
        private final MethodKey methodKey = new MethodKey(name, methodDescriptor);
        private boolean annotated = false;
        private boolean firstLineNumberVisited = false;

        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          for (TMHAssertionGenerator assertionGenerator : assertionGenerators) {
            if (assertionGenerator.isMyAnnotation(annotationDescriptor) && assertionGenerator.isApplicableMethod(methodDescriptor)) {
              return assertionGenerator.getAnnotationChecker(Opcodes.API_VERSION, () -> {
                annotatedMethods.computeIfAbsent(methodKey, k -> new InstrumentationInfo())
                  .assertionGenerators.add(assertionGenerator);
                annotated = true;
              });
            }
          }
          return super.visitAnnotation(annotationDescriptor, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
          super.visitLineNumber(line, start);
          if (annotated && !firstLineNumberVisited) {
            annotatedMethods.get(methodKey).methodStartLineNumber = line;
            firstLineNumberVisited = true;
          }
        }
      };
    }
  }

  private static final class Instrumenter extends ClassVisitor {
    private final Map<MethodKey, InstrumentationInfo> myAnnotatedMethods;

    Instrumenter(ClassVisitor writer, Map<MethodKey, InstrumentationInfo> annotatedMethods) {
      super(Opcodes.API_VERSION, writer);
      myAnnotatedMethods = annotatedMethods;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      InstrumentationInfo instrumentationInfo = myAnnotatedMethods.get(new MethodKey(name, descriptor));
      if (instrumentationInfo == null) {
        return super.visitMethod(access, name, descriptor, signature, exceptions);
      }
      return new FailSafeMethodVisitor(Opcodes.API_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
        private Label myPrologueStart;
        private Label myOriginalFirstLabel;

        @Override
        public void visitCode() {
          myPrologueStart = new Label();
          mv.visitLabel(myPrologueStart);
          for (TMHAssertionGenerator generator : instrumentationInfo.assertionGenerators) {
            generator.generateAssertion(mv, instrumentationInfo.methodStartLineNumber);
          }
          super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
          if (myOriginalFirstLabel == null) {
            myOriginalFirstLabel = label;
          }
          super.visitLabel(label);
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
          Objects.requireNonNull(myOriginalFirstLabel, "no label was visited before the first visitLocalVariable call");
          Objects.requireNonNull(myPrologueStart, "no code was visited before the first visitLocalVariable call");
          Label newStart = start == myOriginalFirstLabel ? myPrologueStart : start;
          super.visitLocalVariable(name, desc, signature, newStart, end, index);
        }
      };
    }
  }

  private static final class MethodKey {
    final String name;
    final String descriptor;

    private MethodKey(String name, String descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + name.hashCode();
      result = 31 * result + descriptor.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this ||
             obj instanceof MethodKey && ((MethodKey)obj).name.equals(name) && ((MethodKey)obj).descriptor.equals(descriptor);
    }
  }

  private static final class InstrumentationInfo {
    final List<TMHAssertionGenerator> assertionGenerators = new ArrayList<>();
    int methodStartLineNumber = -1;
  }
}
