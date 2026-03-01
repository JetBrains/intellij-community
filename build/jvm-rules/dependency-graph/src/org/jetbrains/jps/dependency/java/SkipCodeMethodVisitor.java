// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.TypePath;

final class SkipCodeMethodVisitor extends MethodVisitor {
  // Skip all code-related visits

  SkipCodeMethodVisitor(MethodVisitor delegate) {
    super(Opcodes.API_VERSION, delegate);
  }

  @Override
  public void visitInsn(int opcode) {
    // Skip
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    // Skip
  }

  @Override
  public void visitVarInsn(int opcode, int varIndex) {
    // Skip
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    // Skip
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    // Skip
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
    // Skip
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
    // Skip
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
    // Skip
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    // Skip
  }

  @Override
  public void visitLabel(Label label) {
    // Skip
  }

  @Override
  public void visitLdcInsn(Object value) {
    // Skip
  }

  @Override
  public void visitIincInsn(int varIndex, int increment) {
    // Skip
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    // Skip
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    // Skip
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    // Skip
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    // Skip
  }

  @Override
  public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
    // Skip
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    // Skip
  }

  @Override
  public void visitCode() {
    // Skip
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
    return null; // Skip
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
    return null; // Skip
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
    return null; // Skip
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    // Skip
  }

  @Override
  public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
    // Skip
  }
}
