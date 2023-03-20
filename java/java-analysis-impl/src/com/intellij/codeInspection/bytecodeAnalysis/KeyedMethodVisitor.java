// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Out;

abstract class KeyedMethodVisitor extends ClassVisitor {
  private static final int STABLE_FLAGS = Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;

  KeyedMethodVisitor() {
    super(Opcodes.API_VERSION);
  }

  String className;
  private boolean stableClass;

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    className = name;
    stableClass = (access & Opcodes.ACC_FINAL) != 0;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    MethodNode node = new MethodNode(Opcodes.API_VERSION, access, name, desc, signature, exceptions);
    Member method = new Member(className, node.name, node.desc);
    boolean stable = stableClass || (node.access & STABLE_FLAGS) != 0 || "<init>".equals(node.name);
    return visitMethod(node, method, new EKey(method, Out, stable));
  }

  @Nullable
  abstract MethodVisitor visitMethod(final MethodNode node, Member method, final EKey key);
}