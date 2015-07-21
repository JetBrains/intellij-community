/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.testme.instrumentation;

import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

public class Instrumenter extends ClassVisitor {
  protected final ClassVisitor myClassVisitor;
  private final String myClassName;
  private final String myInternalClassName;
  private final InstrumentedMethodsFilter myMethodFilter;
  private final String[] myMethodNames;
  private int myCurrentMethodCount;
  private boolean myVisitedStaticBlock;

  private static final String METHODS_VISITED = "__$methodsVisited$__";
  private static final String METHODS_VISITED_CLASS = "[Z";

  public Instrumenter(ClassVisitor classVisitor, String className, String[] methodNames) {
    super(Opcodes.ASM5, classVisitor);
    myClassVisitor = classVisitor;
    myMethodFilter = new InstrumentedMethodsFilter(className);
    myClassName = className.replace('$', '.'); // for inner classes
    myInternalClassName = className.replace('.', '/');
    myMethodNames = methodNames;
  }

  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myMethodFilter.visit(version, access, name, signature, superName, interfaces);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  public MethodVisitor visitMethod(final int access,
                                   final String name,
                                   final String desc,
                                   final String signature,
                                   final String[] exceptions) {
    final MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
    if (mv == null) return mv;
    if ("<clinit>".equals(name)) {
      myVisitedStaticBlock = true;
      return new StaticBlockMethodVisitor(mv);
    }

    if (!myMethodFilter.shouldVisitMethod(access, name, desc, signature, exceptions)) return mv;

    assert  myCurrentMethodCount < myMethodNames.length;

    return new MethodVisitor(Opcodes.ASM5, mv) {
      final int myMethodId = myCurrentMethodCount++;

      public void visitCode() {
        visitFieldInsn(Opcodes.GETSTATIC, myInternalClassName, METHODS_VISITED, METHODS_VISITED_CLASS);
        pushInstruction(this, myMethodId);
        visitInsn(Opcodes.ICONST_1);
        visitInsn(Opcodes.BASTORE);

        super.visitCode();
      }
    };
  }

  @Override
  public void visitEnd() {
    visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, METHODS_VISITED,
            METHODS_VISITED_CLASS, null, null);

    if (!myVisitedStaticBlock) {
      MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
      mv = new StaticBlockMethodVisitor(mv);
      mv.visitCode();
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(myMethodNames.length + 2, 1);
      mv.visitEnd();
    }
    super.visitEnd();
  }

  private class StaticBlockMethodVisitor extends MethodVisitor {
    public StaticBlockMethodVisitor(MethodVisitor mv) {
      super(Opcodes.ASM5, mv);
    }

    public void visitCode() {
      super.visitCode();

      pushInstruction(this, myMethodNames.length);
      visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
      visitFieldInsn(Opcodes.PUTSTATIC, myInternalClassName, METHODS_VISITED, METHODS_VISITED_CLASS);

      pushInstruction(this, myMethodNames.length);

      visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");

      for(int i = 0; i < myMethodNames.length; ++i) {
        visitInsn(Opcodes.DUP);
        pushInstruction(this, i);
        visitLdcInsn(myMethodNames[i]);
        visitInsn(Opcodes.AASTORE);
      }

      visitVarInsn(Opcodes.ASTORE, 0);

      Label startLabel = new Label();
      visitLabel(startLabel);

      visitLdcInsn(myClassName);
      visitFieldInsn(Opcodes.GETSTATIC, myInternalClassName, METHODS_VISITED, METHODS_VISITED_CLASS);
      visitVarInsn(Opcodes.ALOAD, 0);
      visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "trace", "(Ljava/lang/String;[Z[Ljava/lang/String;)V", false);

      Label endLabel = new Label();
      visitLabel(endLabel);

      visitLocalVariable("methodNames", "[Ljava/lang/String;", null, startLabel, endLabel, 0);
      // no return here
    }

    public void visitMaxs(int maxStack, int maxLocals) {
      final int ourMaxStack = myMethodNames.length + 2;
      final int ourMaxLocals = 1;

      super.visitMaxs(Math.max(ourMaxStack, maxStack), Math.max(ourMaxLocals, maxLocals));
    }
  }

  private static void pushInstruction(MethodVisitor mv, int operand) {
    if (operand < Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, operand);
    else mv.visitIntInsn(Opcodes.SIPUSH, operand);
  }
}