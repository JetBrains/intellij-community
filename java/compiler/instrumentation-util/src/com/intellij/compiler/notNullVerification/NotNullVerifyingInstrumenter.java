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
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class NotNullVerifyingInstrumenter extends ClassVisitor implements Opcodes {
  private static final String IAE_CLASS_NAME = "java/lang/IllegalArgumentException";
  private static final String ISE_CLASS_NAME = "java/lang/IllegalStateException";

  private static final String ANNOTATION_DEFAULT_METHOD = "value";

  @SuppressWarnings("SSBasedInspection") private static final String[] EMPTY_STRING_ARRAY = new String[0];
  private final Map<String, Map<Integer, String>> myMethodParamNames;

  private String myClassName;
  private boolean myIsModification = false;
  private RuntimeException myPostponedError;
  private final AuxiliaryMethodGenerator myAuxGenerator;
  private final Set<String> myNotNullAnnos = new HashSet<String>();
  private boolean myStatic;
  private boolean myInner;

  private NotNullVerifyingInstrumenter(final ClassVisitor classVisitor, ClassReader reader, String[] notNullAnnotations) {
    super(Opcodes.API_VERSION, classVisitor);
    for (String annotation : notNullAnnotations) {
      myNotNullAnnos.add("L" + annotation.replace('.', '/') + ";");
    }
    myMethodParamNames = getAllParameterNames(reader);
    myAuxGenerator = new AuxiliaryMethodGenerator(reader);
  }

  public static boolean processClassFile(final FailSafeClassReader reader, final ClassVisitor writer, String[] notNullAnnotations) {
    NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer, reader, notNullAnnotations);
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

        final Map<Integer, Integer> paramSlots = new LinkedHashMap<Integer, Integer>(); // map: localVariableSlot -> methodParameterIndex
        int slotIndex = isStatic(access) ? 0 : 1;
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
    myStatic = isStatic(access);
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    myInner = true;
  }

  private static class NotNullState {
    String message;
    String exceptionType;
    final String notNullAnno;

    NotNullState(String notNullAnno, String exceptionType) {
      this.notNullAnno = notNullAnno;
      this.exceptionType = exceptionType;
    }

    String getNullParamMessage(String paramName) {
      if (message != null) return message;

      final String shortName = getAnnoShortName();
      if (paramName != null) return "Argument for @" + shortName + " parameter '%s' of %s.%s must not be null";
      return "Argument %s for @" + shortName + " parameter of %s.%s must not be null";
    }

    String getNullResultMessage() {
      if (message != null) return message;

      final String shortName = getAnnoShortName();
      return "@" + shortName + " method %s.%s must not return null";
    }

    private String getAnnoShortName() {
      String fullName = notNullAnno.substring(1, notNullAnno.length() - 1); // "Lpk/name;" -> "pk/name"
      return fullName.substring(fullName.lastIndexOf('/') + 1);
    }
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, String[] exceptions) {
    if ((access & Opcodes.ACC_BRIDGE) != 0) {
      return new FailSafeMethodVisitor(Opcodes.API_VERSION, super.visitMethod(access, name, desc, signature, exceptions));
    }

    final Type[] args = Type.getArgumentTypes(desc);

    boolean hasOuterClassParameter = myInner && !myStatic && "<init>".equals(name);

    // see http://forge.ow2.org/tracker/?aid=307392&group_id=23&atid=100023&func=detail
    final int syntheticCount = signature == null ? 0 : hasOuterClassParameter ? 1 : Math.max(0, args.length - getSignatureParameterCount(signature));

    // workaround for ASM's workaround for javac bug: http://forge.ow2.org/tracker/?func=detail&aid=317788&group_id=23&atid=100023
    final int paramAnnotationOffset = signature == null ? 0 : hasOuterClassParameter ? Math.max(0, args.length - getSignatureParameterCount(signature) - 1) : 0;

    final Type returnType = Type.getReturnType(desc);
    final MethodVisitor v = cv.visitMethod(access, name, desc, signature, exceptions);
    final Map<Integer, String> paramNames = myMethodParamNames.get(myClassName + '.' + name + desc);
    return new FailSafeMethodVisitor(Opcodes.API_VERSION, v) {
      private final Map<Integer, NotNullState> myNotNullParams = new LinkedHashMap<Integer, NotNullState>();
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

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        AnnotationVisitor av = mv.visitTypeAnnotation(typeRef, null, desc, visible);
        if (typePath != null) return av;

        TypeReference ref = new TypeReference(typeRef);
        if (ref.getSort() == TypeReference.METHOD_RETURN) {
          return checkNotNullMethod(desc, av);
        }
        if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER) {
          return checkNotNullParameter(ref.getFormalParameterIndex() + syntheticCount, desc, av);
        }
        return av;
      }

      public AnnotationVisitor visitParameterAnnotation(final int parameter, final String anno, final boolean visible) {
        AnnotationVisitor base = mv.visitParameterAnnotation(parameter, anno, visible);
        if (parameter < paramAnnotationOffset) return base;

        return checkNotNullParameter(parameter - paramAnnotationOffset, anno, base);
      }

      private AnnotationVisitor checkNotNullParameter(int parameter, String anno, AnnotationVisitor av) {
        if (isReferenceType(args[parameter]) && myNotNullAnnos.contains(anno)) {
          NotNullState state = new NotNullState(anno, IAE_CLASS_NAME);
          myNotNullParams.put(parameter, state);
          return collectNotNullArgs(av, state);
        }

        return av;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String anno, boolean isRuntime) {
        return checkNotNullMethod(anno, mv.visitAnnotation(anno, isRuntime));
      }

      private AnnotationVisitor checkNotNullMethod(String anno, AnnotationVisitor av) {
        if (isReferenceType(returnType) && myNotNullAnnos.contains(anno)) {
          myMethodNotNull = new NotNullState(anno, ISE_CLASS_NAME);
          return collectNotNullArgs(av, myMethodNotNull);
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
          String descrPattern = state.getNullParamMessage(paramName);
          String[] args = state.message != null
                          ? EMPTY_STRING_ARRAY 
                          : new String[]{paramName != null ? paramName : String.valueOf(param - syntheticCount), myClassName, name};
          reportError(state.exceptionType, end, descrPattern, args);
        }
      }

      @Override
      public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        boolean isParameterOrThisRef = isStatic(access) ? index < args.length : index <= args.length;
        Label label = (isParameterOrThisRef && myStartGeneratedCodeLabel != null) ? myStartGeneratedCodeLabel : start;
        mv.visitLocalVariable(name, desc, signature, label, end, index);
      }

      @Override
      public void visitInsn(int opcode) {
        if (opcode == ARETURN) {
          if (myMethodNotNull != null) {
            mv.visitInsn(DUP);
            final Label skipLabel = new Label();
            mv.visitJumpInsn(IFNONNULL, skipLabel);
            String descrPattern = myMethodNotNull.getNullResultMessage();
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

  private static boolean isStatic(int access) {
    return (access & ACC_STATIC) != 0;
  }

  private static int getSignatureParameterCount(String signature) {
    final int[] count = {0};
    new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM6) {
      @Override
      public SignatureVisitor visitParameterType() {
        count[0]++;
        return super.visitParameterType();
      }
    });
    return count[0];
  }

  private static boolean isReferenceType(final Type type) {
    return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
  }

  private void registerError(String methodName, @SuppressWarnings("SameParameterValue") String operationName, Throwable e) {
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


