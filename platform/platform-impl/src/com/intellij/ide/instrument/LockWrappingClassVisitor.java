// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.instrument;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.AdviceAdapter;

import java.util.Set;

class LockWrappingClassVisitor extends ClassVisitor {
  private static final @NonNls Set<String> METHODS_TO_WRAP = ContainerUtil.set(
    "paint",
    "paintComponent",
    "paintChildren",
    "doLayout",
    "layout",
    "getPreferredSize",
    "paintTrack",
    "getListCellRendererComponent",
    "getElementText"
  );
  private final Set<String> myMethodsToAnnotate;

  LockWrappingClassVisitor(ClassWriter cw, Set<String> methodsToAnnotate) {
    super(Opcodes.API_VERSION, cw);
    myMethodsToAnnotate = methodsToAnnotate;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);


    if (METHODS_TO_WRAP.contains(name) || myMethodsToAnnotate.contains(name)) {
      return new MyAdviceAdapter(api, mv, access, name, descriptor);
    }
    return mv;
  }

  /*
  L0
    LINENUMBER 20 L0
    INVOKESTATIC com/intellij/openapi/application/ex/ApplicationUtil.acquireWriteIntentLockIfNeeded ()Z
    ISTORE 0
   L2
    LINENUMBER 24 L2
    ILOAD 0
    INVOKESTATIC com/intellij/openapi/application/ex/ApplicationUtil.releaseWriteIntentLockIfNeeded (Z)V
   */

  private static class MyAdviceAdapter extends AdviceAdapter {
    private static final String applicationUtil = "com/intellij/openapi/application/ex/ApplicationUtil";
    private static final String acquireLock = "acquireWriteIntentLockIfNeeded";
    private static final String releaseLock = "releaseWriteIntentLockIfNeeded";
    private static final String acquireLockSignature = "()Z";
    private static final String releaseLockSignature = "(Z)V";

    private int newVarIndex;

    /**
     * Constructs a new {@link AdviceAdapter}.
     *
     * @param api           the ASM API version implemented by this visitor. Must be one of {@link
     *                      Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link Opcodes#ASM7}.
     * @param methodVisitor the method visitor to which this adapter delegates calls.
     * @param access        the method's access flags (see {@link Opcodes}).
     * @param name          the method's name.
     * @param descriptor    the method's descriptor (see {@link Type Type}).
     */
    protected MyAdviceAdapter(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
      super(api, methodVisitor, access, name, descriptor);
    }

    @Override
    protected void onMethodEnter() {
      newVarIndex = newLocal(Type.BOOLEAN_TYPE);
      mv.visitMethodInsn(INVOKESTATIC,
                         applicationUtil,
                         acquireLock,
                         acquireLockSignature,
                         false);
      mv.visitVarInsn(ISTORE, newVarIndex);
    }

    @Override
    protected void onMethodExit(int opcode) {
      mv.visitVarInsn(ILOAD, newVarIndex);
      mv.visitMethodInsn(INVOKESTATIC,
                         applicationUtil,
                         releaseLock,
                         releaseLockSignature,
                         false);
    }
  }
}
