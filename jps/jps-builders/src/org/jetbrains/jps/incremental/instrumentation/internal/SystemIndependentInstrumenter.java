/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.instrumentation.internal;

import org.jetbrains.org.objectweb.asm.*;
import sun.management.counter.perf.InstrumentationException;

class SystemIndependentInstrumenter extends ClassVisitor {
  static final String ANNOTATION = "@SystemIndependent";
  static final String ANNOTATION_CLASS = "org/jetbrains/annotations/SystemIndependent";
  static final String ASSERTION_SIGNATURE = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";

  private final String myAssertionMethodName;

  private String myClassName;
  private boolean myAssertionAdded;

  SystemIndependentInstrumenter(ClassWriter writer, String assertionMethodName) {
    super(Opcodes.API_VERSION, writer);
    myAssertionMethodName = assertionMethodName;
  }

  public boolean isAssertionAdded() {
    return myAssertionAdded;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myClassName = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    Type[] argumentTypes = Type.getArgumentTypes(desc);

    Parameter[] parameters = new Parameter[argumentTypes.length];

    int slot = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
    for (int i = 0; i < argumentTypes.length; i++) {
      Type argumentType = argumentTypes[i];

      Parameter parameter = new Parameter();
      parameter.name = Integer.toString(i);
      parameter.slot = slot;
      parameter.isString = argumentType.getSort() == Type.OBJECT && "java.lang.String".equals(argumentType.getClassName());

      parameters[i] = parameter;

      slot += argumentType.getSize();
    }

    return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
      private int myParameterIndex = 0;

      @Override
      public void visitParameter(String name, int access) {
        parameters[myParameterIndex].name = name;
        myParameterIndex++;
        super.visitParameter(name, access);
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        if (typePath == null) {
          TypeReference ref = new TypeReference(typeRef);
          if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && ("L" + ANNOTATION_CLASS + ";").equals(desc)) {
            parameters[ref.getFormalParameterIndex()].isSystemIndependent = true;
          }
        }
        return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
      }

      @Override
      public void visitCode() {
        super.visitCode();

        for (Parameter parameter : parameters) {
          if (parameter.isSystemIndependent) {
            if (!parameter.isString) {
              throw new InstrumentationException("Only String can be annotated as " + ANNOTATION);
            }
            addAssertionFor(parameter.name, parameter.slot);
            myAssertionAdded = true;
          }
        }
      }

      private void addAssertionFor(String parameterName, int parameterSlot) {
        visitLdcInsn(myClassName);
        visitLdcInsn(name);
        visitLdcInsn(parameterName);
        visitVarInsn(Opcodes.ALOAD, parameterSlot);
        visitMethodInsn(Opcodes.INVOKESTATIC, myClassName, myAssertionMethodName, ASSERTION_SIGNATURE, false);
      }
    };
  }

  private static class Parameter {
    String name;
    int slot;
    boolean isString;
    boolean isSystemIndependent;
  }
}
