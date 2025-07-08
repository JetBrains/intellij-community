// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.FieldNode;
import org.jetbrains.org.objectweb.asm.tree.InsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class JavaAbiClassFilter extends ClassVisitor {
  public static final String MODULE_INFO_CLASS_NAME = "module-info";
  private boolean isAbiClass;
  private boolean allowPackageLocalMethods;
  private final Set<String> myExcludedClasses = new HashSet<>();
  private final List<FieldNode> myFields = new ArrayList<>();
  private final List<MethodNode> myMethods = new ArrayList<>();

  private JavaAbiClassFilter(ClassVisitor delegate) {
    super(Opcodes.API_VERSION, delegate);
  }

  public static byte @Nullable [] filter(byte[] classBytes) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
      @Override
      protected String getCommonSuperClass(String type1, String type2) {
        return null;
      }
    };
    JavaAbiClassFilter abiVisitor = new JavaAbiClassFilter(writer);
    new FailSafeClassReader(classBytes).accept(
      abiVisitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
    );
    return abiVisitor.isAbiClass? writer.toByteArray() : null;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    isAbiClass = MODULE_INFO_CLASS_NAME.equals(name) || isAbiVisible(access);
    allowPackageLocalMethods = name.contains("/android/");   // todo: temporary condition to enable android tests compilation
    if (!isAbiClass) {
      myExcludedClasses.add(name);
    }
    super.visit(version, access, name, signature, superName, interfaces);
  }

  private static boolean isAbiVisible(int access) {
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
  }

  private static boolean isPackageLocal(int access) {
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    if (isAbiVisible(access)) {
      FieldNode field = new FieldNode(Opcodes.API_VERSION, access, name, descriptor, signature, value);
      myFields.add(field);
      return field;
    }
    return null;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    if (isAbiVisible(access) || (allowPackageLocalMethods && isPackageLocal(access))) {
      MethodNode method = new AbiMethod(access, name, descriptor, signature, exceptions);
      myMethods.add(method);
      return method;
    }
    return null;
  }

  @Override
  public void visitEnd() {
    Collections.sort(myFields, Comparator.comparing(f -> f.name));
    for (FieldNode field : myFields) {
      field.accept(cv);
    }
    Collections.sort(myMethods, Comparator.comparing(m -> m.name));
    for (MethodNode method : myMethods) {
      method.accept(cv);
    }
    super.visitEnd();
  }

  @Override
  public void visitNestMember(String nestMember) {
    if (nestMember == null || !myExcludedClasses.contains(nestMember)) {
      super.visitNestMember(nestMember);
    }
  }

  @Override
  public void visitPermittedSubclass(String permittedSubclass) {
    if (permittedSubclass == null || !myExcludedClasses.contains(permittedSubclass)) {
      super.visitPermittedSubclass(permittedSubclass);
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    // innerName == null for anonymous classes
    if (isAbiVisible(access) && innerName != null && !myExcludedClasses.contains(name)) {
      super.visitInnerClass(name, outerName, innerName, access);
    }
  }

  private static final class AbiMethod extends MethodNode {
    private static final InsnNode NOP_INSTRUCTION = new InsnNode(Opcodes.NOP);

    AbiMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      super(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions);

      if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
        // in a valid bytecode, non-abstract and non-native methods must have a code attribute
        instructions.add(NOP_INSTRUCTION);
      }
    }
  }
}
