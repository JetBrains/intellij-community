// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.instrument;

import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.AdviceAdapter;

import java.util.Set;

import static com.intellij.openapi.application.ex.ApplicationManagerEx.getApplicationEx;
import static com.intellij.util.ui.EDT.isCurrentThreadEdt;

public final class LockWrappingClassVisitor extends ClassVisitor {
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
  private final @NotNull String myClassName;
  private final @NotNull Set<String> myMethodsToAnnotate;

  LockWrappingClassVisitor(ClassWriter cw,
                           @NotNull String className,
                           @NotNull Set<String> methodsToAnnotate) {
    super(Opcodes.API_VERSION, cw);
    myClassName = className.replace('/', '.');
    myMethodsToAnnotate = methodsToAnnotate;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

    if (METHODS_TO_WRAP.contains(name) || myMethodsToAnnotate.contains(name)) {
      return new MyAdviceAdapter(mv, access, name, descriptor);
    }
    return mv;
  }

  /**
   * L0
   * LINENUMBER 20 L0
   * INVOKESTATIC {@link LockWrappingClassVisitor#acquireWriteIntentLockIfNeeded} (Ljava/lang/String;)Z
   * ISTORE 0
   * L2
   * LINENUMBER 24 L2
   * ILOAD 0
   * INVOKESTATIC {@link LockWrappingClassVisitor#releaseWriteIntentLockIfNeeded} (Z)V
   */

  private class MyAdviceAdapter extends AdviceAdapter {
    private static final String applicationUtil = "com/intellij/ide/instrument/LockWrappingClassVisitor";
    private static final String acquireLock = "acquireWriteIntentLockIfNeeded";
    private static final String releaseLock = "releaseWriteIntentLockIfNeeded";
    private static final String acquireLockSignature = "(Ljava/lang/String;)Z";
    private static final String releaseLockSignature = "(Z)V";

    private int newVarIndex;

    /**
     * Constructs a new {@link AdviceAdapter}.
     *
     * @param methodVisitor the method visitor to which this adapter delegates calls.
     * @param access        the method's access flags (see {@link Opcodes}).
     * @param name          the method's name.
     * @param descriptor    the method's descriptor (see {@link Type Type}).
     */
    protected MyAdviceAdapter(MethodVisitor methodVisitor, int access, String name, String descriptor) {
      super(LockWrappingClassVisitor.this.api, methodVisitor, access, name, descriptor);
    }

    @Override
    protected void onMethodEnter() {
      newVarIndex = newLocal(Type.BOOLEAN_TYPE);
      mv.visitLdcInsn(myClassName + "#" + getName());
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

  /**
   * Acquires IW lock if it's not acquired by the current thread.
   * <p>
   * Used in {@link MyAdviceAdapter#acquireLock}.
   *
   * @param invokedClassFqn fully qualified name of the class requiring the write intent lock.
   * @return {@code true} if IW lock was acquired, or {@code false} if it is held by the current thread already.
   */
  @SuppressWarnings("unused")
  public static boolean acquireWriteIntentLockIfNeeded(@NotNull String invokedClassFqn) {
    if (!isCurrentThreadEdt()) return false; // do not do anything for non-EDT calls

    ApplicationEx application = getApplicationEx();
    if (application.isWriteThread()) return false;

    application.acquireWriteIntentLock(invokedClassFqn);
    return true;
  }

  /**
   * Releases IW lock if the parameter is {@code true}.
   * <p>
   * Used in {@link MyAdviceAdapter#releaseLock}.
   *
   * @param needed whether IW lock should be released or not
   */
  @SuppressWarnings("unused")
  public static void releaseWriteIntentLockIfNeeded(boolean needed) {
    if (needed) {
      getApplicationEx().releaseWriteIntentLock();
    }
  }
}
