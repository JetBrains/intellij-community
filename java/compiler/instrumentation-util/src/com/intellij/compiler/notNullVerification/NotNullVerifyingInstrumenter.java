/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.FailSafeMethodVisitor;
import org.jetbrains.org.objectweb.asm.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ven
 */
public class NotNullVerifyingInstrumenter extends ClassVisitor implements Opcodes {
  private static final String NOT_NULL_CLASS_NAME = "org/jetbrains/annotations/NotNull";
  private static final String NOT_NULL_TYPE = "L"+ NOT_NULL_CLASS_NAME + ";";
  private static final String SYNTHETIC_CLASS_NAME = "java/lang/Synthetic";
  private static final String SYNTHETIC_TYPE = "L" + SYNTHETIC_CLASS_NAME + ";";
  private static final String IAE_CLASS_NAME = "java/lang/IllegalArgumentException";
  private static final String ISE_CLASS_NAME = "java/lang/IllegalStateException";

  private static final String ANNOTATION_DEFAULT_METHOD = "value";

  private static final String NULL_ARG_MESSAGE_INDEXED = "Argument %s for @NotNull parameter of %s.%s must not be null";
  private static final String NULL_ARG_MESSAGE_NAMED = "Argument for @NotNull parameter '%s' of %s.%s must not be null";
  private static final String NULL_RESULT_MESSAGE = "@NotNull method %s.%s must not return null";
  @SuppressWarnings("SSBasedInspection") private static final String[] EMPTY_STRING_ARRAY = new String[0];
  private final Map<String, Map<Integer, String>> myMethodParamNames;

  private String myClassName;
  private boolean myIsModification = false;
  private RuntimeException myPostponedError;
  private final AuxiliaryMethodGenerator myAuxGenerator;

  private NotNullVerifyingInstrumenter(final ClassVisitor classVisitor, ClassReader reader) {
    super(Opcodes.API_VERSION, classVisitor);
    myMethodParamNames = getAllParameterNames(reader);
    myAuxGenerator = new AuxiliaryMethodGenerator(reader);
  }

  public static boolean processClassFile(final FailSafeClassReader reader, final ClassVisitor writer) {
    final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer, reader);
    reader.accept(instrumenter, 0);
    instrumenter.myAuxGenerator.generateReportingMethod(writer);
    return instrumenter.isModification();
  }

  private static Map<String, Map<Integer, String>> getAllParameterNames(ClassReader reader) {
    final Map<String, Map<Integer, String>> methodParamNames = new LinkedHashMap<String, Map<Integer, String>>();

    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      private String myClassName = null;

      public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        myClassName = name;
      }

      public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        final String methodName = myClassName + '.' + name + desc;
        final Map<Integer, String> names = new LinkedHashMap<Integer, String>();
        final Type[] args = Type.getArgumentTypes(desc);
        methodParamNames.put(methodName, names);

        final boolean isStatic = (access & ACC_STATIC) != 0;

        final Map<Integer, Integer> paramSlots = new LinkedHashMap<Integer, Integer>(); // map: localVariableSlot -> methodParameterIndex
        int slotIndex = isStatic? 0 : 1;
        for (int paramIndex = 0; paramIndex < args.length; paramIndex++) {
          final Type arg = args[paramIndex];
          paramSlots.put(slotIndex, paramIndex);
          slotIndex += arg.getSize();
        }

        return new MethodVisitor(api) {

          @Override
          public void visitLocalVariable(String name2, String desc, String signature, Label start, Label end, int slotIndex) {
            final Integer paramIndex = paramSlots.get(slotIndex);
            if (paramIndex != null) {
              names.put(paramIndex, name2);
            }
          }
        };
      }
    }, 0);
    return methodParamNames;
  }

  public boolean isModification() {
    return myIsModification;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    myClassName = name;
  }

  private static class NotNullState {
    String message;
    String exceptionType;

    NotNullState(String exceptionType) {
      this.exceptionType = exceptionType;
    }
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, String desc, String signature, String[] exceptions) {
    if ((access & Opcodes.ACC_BRIDGE) != 0) {
      return new FailSafeMethodVisitor(Opcodes.API_VERSION, super.visitMethod(access, name, desc, signature, exceptions));
    }

    final Type[] args = Type.getArgumentTypes(desc);
    final Type returnType = Type.getReturnType(desc);
    final MethodVisitor v = cv.visitMethod(access, name, desc, signature, exceptions);
    final Map<Integer, String> paramNames = myMethodParamNames.get(myClassName + '.' + name + desc);
    return new FailSafeMethodVisitor(Opcodes.API_VERSION, v) {
      private final Map<Integer, NotNullState> myNotNullParams = new LinkedHashMap<Integer, NotNullState>();
      private int mySyntheticCount = 0;
      private NotNullState myMethodNotNull;
      private Label myStartGeneratedCodeLabel;

      private AnnotationVisitor collectNotNullArgs(AnnotationVisitor base, final NotNullState state) {
        return new AnnotationVisitor(Opcodes.API_VERSION, base) {
          @Override
          public void visit(String methodName, Object o) {
            if (ANNOTATION_DEFAULT_METHOD.equals(methodName) && !((String) o).isEmpty()) {
              state.message = (String) o;
            }
            else if ("exception".equals(methodName) && o instanceof Type && !((Type)o).getClassName().equals(Exception.class.getName())) {
              state.exceptionType = ((Type)o).getInternalName();
            }
            super.visit(methodName, o);
          }
        };
      }

      public AnnotationVisitor visitParameterAnnotation(final int parameter, final String anno, final boolean visible) {
        AnnotationVisitor av = mv.visitParameterAnnotation(parameter, anno, visible);
        if (isReferenceType(args[parameter]) && anno.equals(NOT_NULL_TYPE)) {
          NotNullState state = new NotNullState(IAE_CLASS_NAME);
          myNotNullParams.put(new Integer(parameter), state);
          av = collectNotNullArgs(av, state);
        }
        else if (anno.equals(SYNTHETIC_TYPE)) {
          // see http://forge.ow2.org/tracker/?aid=307392&group_id=23&atid=100023&func=detail
          mySyntheticCount++;
        }

        return av;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String anno, boolean isRuntime) {
        AnnotationVisitor av = mv.visitAnnotation(anno, isRuntime);
        if (isReferenceType(returnType) && anno.equals(NOT_NULL_TYPE)) {
          myMethodNotNull = new NotNullState(ISE_CLASS_NAME);
          av = collectNotNullArgs(av, myMethodNotNull);
        }

        return av;
      }

      @Override
      public void visitCode() {
        if (myNotNullParams.size() > 0) {
          myStartGeneratedCodeLabel = new Label();
          mv.visitLabel(myStartGeneratedCodeLabel);
        }
        for (Map.Entry<Integer, NotNullState> entry : myNotNullParams.entrySet()) {
          Integer param = entry.getKey();
          int var = ((access & ACC_STATIC) == 0) ? 1 : 0;
          for (int i = 0; i < param; ++i) {
            var += args[i].getSize();
          }
          mv.visitVarInsn(ALOAD, var);

          Label end = new Label();
          mv.visitJumpInsn(IFNONNULL, end);

          NotNullState state = entry.getValue();
          String paramName = paramNames == null ? null : paramNames.get(param);
          String descrPattern = state.message != null
                                ? state.message
                                : paramName != null ? NULL_ARG_MESSAGE_NAMED : NULL_ARG_MESSAGE_INDEXED;
          String[] args = state.message != null
                          ? EMPTY_STRING_ARRAY 
                          : new String[]{paramName != null ? paramName : String.valueOf(param - mySyntheticCount), myClassName, name};
          reportError(state.exceptionType, end, descrPattern, args);
        }
      }

      @Override
      public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        final boolean isStatic = (access & ACC_STATIC) != 0;
        final boolean isParameterOrThisRef = isStatic ? index < args.length : index <= args.length;
        final Label label = (isParameterOrThisRef && myStartGeneratedCodeLabel != null) ? myStartGeneratedCodeLabel : start;
        mv.visitLocalVariable(name, desc, signature, label, end, index);
      }

      @Override
      public void visitInsn(int opcode) {
        if (opcode == ARETURN) {
          if (myMethodNotNull != null) {
            mv.visitInsn(DUP);
            final Label skipLabel = new Label();
            mv.visitJumpInsn(IFNONNULL, skipLabel);
            String descrPattern = myMethodNotNull.message != null ? myMethodNotNull.message : NULL_RESULT_MESSAGE;
            String[] args = myMethodNotNull.message != null ? EMPTY_STRING_ARRAY : new String[]{myClassName, name};
            reportError(myMethodNotNull.exceptionType, skipLabel, descrPattern, args);
          }
        }

        mv.visitInsn(opcode);
      }

      private void reportError(final String exceptionClass, final Label end, final String descrPattern, final String[] args) {
        myAuxGenerator.reportError(mv, myClassName, exceptionClass, descrPattern, args);

        mv.visitLabel(end);

        myIsModification = true;
        processPostponedErrors();
      }

      @Override
      public void visitMaxs(final int maxStack, final int maxLocals) {
        try {
          super.visitMaxs(maxStack, maxLocals);
        }
        catch (Throwable e) {
          //noinspection SpellCheckingInspection
          registerError(name, "visitMaxs", e);
        }
      }
    };
  }

  private static boolean isReferenceType(final Type type) {
    return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
  }

  private void registerError(String methodName, String operationName, Throwable e) {
    if (myPostponedError == null) {
      // throw the first error that occurred
      Throwable err = e.getCause();
      if (err == null) {
        err = e;
      }
      final StringBuilder message = new StringBuilder();
      message.append("Operation '").append(operationName).append("' failed for ").append(myClassName).append(".").append(methodName).append("(): ");
      
      final String errMessage = err.getMessage();
      if (errMessage != null) {
        message.append(errMessage);
      }
      
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      err.printStackTrace(new PrintStream(out));
      message.append('\n').append(out.toString());
      
      myPostponedError = new RuntimeException(message.toString(), err);
    }
    if (myIsModification) {
      processPostponedErrors();
    }
  }

  private void processPostponedErrors() {
    final RuntimeException error = myPostponedError;
    if (error != null) {
      throw error;
    }
  }
}

