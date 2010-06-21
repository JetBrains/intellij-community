/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.notNullVerification;

import org.objectweb.asm.*;

import java.util.ArrayList;

/**
 * @author ven
 * @noinspection HardCodedStringLiteral
 */
public class NotNullVerifyingInstrumenter extends ClassAdapter implements Opcodes {
  private boolean myIsModification = false;
  private boolean myIsNotStaticInner = false;
  private String myClassName;
  private String mySuperName;
  public static final String NOT_NULL = "org/jetbrains/annotations/NotNull";
  public static final String NOT_NULL_ANNO = "L"+ NOT_NULL + ";";
  private static final String ENUM_CLASS_NAME = "java/lang/Enum";
  public static final String IAE_CLASS_NAME = "java/lang/IllegalArgumentException";
  public static final String ISE_CLASS_NAME = "java/lang/IllegalStateException";
  private static final String CONSTRUCTOR_NAME = "<init>";

  public NotNullVerifyingInstrumenter(final ClassVisitor classVisitor) {
    super(classVisitor);
  }

  public boolean isModification() {
    return myIsModification;
  }

  public void visit(final int version,
                    final int access,
                    final String name,
                    final String signature,
                    final String superName,
                    final String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    myClassName = name;
    mySuperName = superName;
  }

  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (myClassName.equals(name)) {
      myIsNotStaticInner = (access & ACC_STATIC) == 0;
    }
  }

  public MethodVisitor visitMethod(
    final int access,
    final String name,
    final String desc,
    final String signature,
    final String[] exceptions) {
    final Type[] args = Type.getArgumentTypes(desc);
    final Type returnType = Type.getReturnType(desc);
    final int startParameter = getStartParameterIndex(name);
    MethodVisitor v = cv.visitMethod(access,
                                     name,
                                     desc,
                                     signature,
                                     exceptions);
    return new MethodAdapter(v) {

      private final ArrayList myNotNullParams = new ArrayList();
      private boolean myIsNotNull = false;
      //private boolean myIsUnmodifiable = false;
      public Label myThrowLabel;
      //public Label myWrapLabel;
      private Label myStartGeneratedCodeLabel;

      public AnnotationVisitor visitParameterAnnotation(
        final int parameter,
        final String anno,
        final boolean visible) {
        AnnotationVisitor av;
        av = mv.visitParameterAnnotation(parameter,
                                         anno,
                                         visible);
        if (isReferenceType(args[parameter]) &&
            anno.equals(NOT_NULL_ANNO)) {
          myNotNullParams.add(new Integer(parameter));
        }
        return av;
      }

      public AnnotationVisitor visitAnnotation(String anno,
                                               boolean isRuntime) {
        final AnnotationVisitor av = mv.visitAnnotation(anno, isRuntime);
        if (isReferenceType(returnType) &&
            anno.equals(NOT_NULL_ANNO)) {
          myIsNotNull = true;
        }

        return av;
      }

      public void visitCode() {
        if (myNotNullParams.size() > 0) {
          myStartGeneratedCodeLabel = new Label();
          mv.visitLabel(myStartGeneratedCodeLabel);
        }
        for (int p = 0; p < myNotNullParams.size(); ++p) {
          int var = ((access & ACC_STATIC) == 0) ? 1 : 0;
          int param = ((Integer)myNotNullParams.get(p)).intValue();
          for (int i = 0; i < param + startParameter; ++i) {
            var += args[i].getSize();
          }
          mv.visitVarInsn(ALOAD, var);

          Label end = new Label();
          mv.visitJumpInsn(IFNONNULL, end);

          generateThrow(IAE_CLASS_NAME,
                        "Argument " + param + " for @NotNull parameter of " + myClassName + "." + name + " must not be null", end);
        }
      }

      public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end,
                                     final int index) {
        final boolean isStatic = (access & ACC_STATIC) != 0;
        final boolean isParameter = isStatic ? index < args.length : index <= args.length;
        mv.visitLocalVariable(name, desc, signature, (isParameter && myStartGeneratedCodeLabel != null) ? myStartGeneratedCodeLabel : start, end, index);
      }

      public void visitInsn(int opcode) {
        if (opcode == ARETURN) {
          if (myIsNotNull) {
            mv.visitInsn(DUP);
            /*generateConditionalThrow("@NotNull method " + myClassName + "." + name + " must not return null",
              "java/lang/IllegalStateException");*/
            if (myThrowLabel == null) {
              Label skipLabel = new Label();
              mv.visitJumpInsn(IFNONNULL, skipLabel);
              myThrowLabel = new Label();
              mv.visitLabel(myThrowLabel);
              generateThrow(ISE_CLASS_NAME, "@NotNull method " + myClassName + "." + name + " must not return null",
                            skipLabel);
            }
            else {
              mv.visitJumpInsn(IFNULL, myThrowLabel);
            }
          }
        }

        mv.visitInsn(opcode);
      }

      private void generateThrow(final String exceptionClass, final String descr, final Label end) {
        String exceptionParamClass = "(Ljava/lang/String;)V";
        mv.visitTypeInsn(NEW, exceptionClass);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(descr);
        mv.visitMethodInsn(INVOKESPECIAL,
                           exceptionClass,
                           CONSTRUCTOR_NAME,
                           exceptionParamClass);
        mv.visitInsn(ATHROW);
        mv.visitLabel(end);

        myIsModification = true;
      }

      public void visitMaxs(final int maxStack, final int maxLocals) {
        try {
          super.visitMaxs(maxStack, maxLocals);
        }
        catch (ArrayIndexOutOfBoundsException e) {
          throw new ArrayIndexOutOfBoundsException("maxs processing failed for method " + name + ": " + e.getMessage());
        }
      }
    };
  }

  private int getStartParameterIndex(final String name) {
    int result = 0;
    if (CONSTRUCTOR_NAME.equals(name)) {
      if (mySuperName.equals(ENUM_CLASS_NAME)) {
        result += 2;
      }
      if (myIsNotStaticInner) {
        result += 1;
      }
    }
    return result;
  }

  private static boolean isReferenceType(final Type type) {
    return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
  }
}
