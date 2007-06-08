package com.intellij.compiler.notNullVerification;

import org.objectweb.asm.*;

import java.util.ArrayList;

/**
 * @author ven
 * @noinspection HardCodedStringLiteral
 */
public class NotNullVerifyingInstrumenter extends ClassAdapter {

  private boolean myIsModification = false;
  private boolean myIsNotStaticInner = false;
  private String myClassName;

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
  }

  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (myClassName.equals(name)) {
      myIsNotStaticInner = (access & Opcodes.ACC_STATIC) == 0;
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
    final int startParameter = "<init>".equals(name) && myIsNotStaticInner ? 1 : 0;
    MethodVisitor v = cv.visitMethod(access,
                                     name,
                                     desc,
                                     signature,
                                     exceptions);
    return new MethodAdapter(v) {

      private ArrayList myNotNullParams = new ArrayList();
      private boolean myIsNotNull = false;
      public Label myThrowLabel;
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
            anno.equals("Lorg/jetbrains/annotations/NotNull;")) {
          myNotNullParams.add(new Integer(parameter));
        }
        return av;
      }

      public AnnotationVisitor visitAnnotation(String anno,
                                               boolean isRuntime) {
        final AnnotationVisitor av = mv.visitAnnotation(anno, isRuntime);
        if (isReferenceType(returnType) &&
            anno.equals("Lorg/jetbrains/annotations/NotNull;")) {
          myIsNotNull = true;
        }

        return av;
      }

      public void visitCode() {
        if (myIsNotNull || myNotNullParams.size() > 0) {
          myStartGeneratedCodeLabel = new Label();
          mv.visitLabel(myStartGeneratedCodeLabel);
        }
        for (int p = 0; p < myNotNullParams.size(); ++p) {
          int var = ((access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;
          int param = ((Integer)myNotNullParams.get(p)).intValue();
          for (int i = 0; i < param + startParameter; ++i) {
            var += args[i].getSize();
          }
          mv.visitVarInsn(Opcodes.ALOAD, var);

          Label end = new Label();
          mv.visitJumpInsn(Opcodes.IFNONNULL, end);

          generateThrow("java/lang/IllegalArgumentException",
                        "Argument " + param + " for @NotNull parameter of " + myClassName + "." + name + " must not be null", end);

        }

        if (myIsNotNull) {
          Label codeStart = new Label();
          mv.visitJumpInsn(Opcodes.GOTO, codeStart);

          myThrowLabel = new Label();
          mv.visitLabel(myThrowLabel);
          //generate throw for method null return
          generateThrow("java/lang/IllegalStateException", "@NotNull method " + myClassName + "." + name + " must not return null",
                        codeStart);
        }
      }

      public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end,
                                     final int index) {
        final boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        final boolean isParameter = isStatic ? index < args.length : index <= args.length;
        mv.visitLocalVariable(name, desc, signature, (isParameter && myStartGeneratedCodeLabel != null) ? myStartGeneratedCodeLabel : start, end, index);
      }

      public void visitInsn(int opcode) {
        if (opcode == Opcodes.ARETURN && myIsNotNull) {
          mv.visitInsn(Opcodes.DUP);
          /*generateConditionalThrow("@NotNull method " + myClassName + "." + name + " must not return null",
                                   "java/lang/IllegalStateException");*/
          mv.visitJumpInsn(Opcodes.IFNULL, myThrowLabel);
        }

        mv.visitInsn(opcode);
      }

      private void generateThrow(final String exceptionClass, final String descr, final Label end) {
        String exceptionParamClass = "(Ljava/lang/String;)V";
        mv.visitTypeInsn(Opcodes.NEW, exceptionClass);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(descr);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                           exceptionClass,
                           "<init>",
                           exceptionParamClass);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(end);

        myIsModification = true;
      }
    };
  }

  private static boolean isReferenceType(final Type type) {
    return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
  }
}
