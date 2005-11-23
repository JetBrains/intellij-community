package com.intellij.compiler.notNullVerification;

import gnu.trove.TIntArrayList;
import org.objectweb.asm.*;

/**
 * @author ven
 */
public class NotNullVerifyingInstrumenter extends ClassAdapter {

  private boolean myIsModification = false;

  public NotNullVerifyingInstrumenter(final ClassVisitor classVisitor) {
    super(classVisitor);
  }

  public boolean isModification() {
    return myIsModification;
  }


  public MethodVisitor visitMethod(
    final int access,
    final String name,
    final String desc,
    final String signature,
    final String[] exceptions) {
    final Type[] args = Type.getArgumentTypes(desc);
    MethodVisitor v = cv.visitMethod(access,
                                     name,
                                     desc,
                                     signature,
                                     exceptions);
    return new MethodAdapter(v) {

      private TIntArrayList myNotNullParams = new TIntArrayList();
      private boolean myIsNotNull = false;

      public AnnotationVisitor visitParameterAnnotation(
        final int parameter,
        final String anno,
        final boolean visible) {
        AnnotationVisitor av;
        av = mv.visitParameterAnnotation(parameter,
                                         anno,
                                         visible);
        if (anno.equals("Lorg/jetbrains/annotations/NotNull;")) {
          myNotNullParams.add(parameter);
        }
        return av;
      }

      public AnnotationVisitor visitAnnotation(String anno,
                                               boolean isRuntime) {
        final AnnotationVisitor av = mv.visitAnnotation(anno, isRuntime);
        if (anno.equals("Lorg/jetbrains/annotations/NotNull;")) {
          myIsNotNull = true;
        }

        return av;
      }

      public void visitCode() {
        for (int p = 0; p < myNotNullParams.size(); ++p) {
          int var = ((access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;
          int param = myNotNullParams.get(p);
          for (int i = 0; i < param; ++i) {
            var += args[i].getSize();
          }
          mv.visitVarInsn(Opcodes.ALOAD, var);
          final String descr = "Argument " + param + " for @NotNull parameter must not be null";

          generateConditionalThrow(descr);
        }
      }

      public void visitInsn(int opcode) {
        if (opcode == Opcodes.ARETURN && myIsNotNull) {
          mv.visitInsn(Opcodes.DUP);
          generateConditionalThrow("@NotNull method must not return null");
        }

        mv.visitInsn(opcode);
      }

      private void generateConditionalThrow(final String descr) {
        String c = "java/lang/AssertionError";
        String d = "(Ljava/lang/Object;)V";
        Label end = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, end);

        mv.visitTypeInsn(Opcodes.NEW, c);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(descr);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                           c,
                           "<init>",
                           d);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(end);
        
        myIsModification = true;
      }
    };
  }

}
