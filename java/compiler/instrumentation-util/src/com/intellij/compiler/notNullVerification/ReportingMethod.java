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

import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * @author peter
 */
class ReportingMethod {
  private static final String STRING_CLASS_NAME = "java/lang/String";
  private static final String OBJECT_CLASS_NAME = "java/lang/Object";
  private static final String CONSTRUCTOR_NAME = "<init>";
  private static final String EXCEPTION_INIT_SIGNATURE = "(L" + STRING_CLASS_NAME + ";)V";

  private final String exceptionClass;
  private final String descrPattern;
  private final int argCount;

  ReportingMethod(String exceptionClass, String descrPattern, int argCount) {
    this.exceptionClass = exceptionClass;
    this.descrPattern = descrPattern;
    this.argCount = argCount;
  }

  String getMethodDesc() {
    String params = "";
    for (int i = 0; i < argCount; i++) {
      params += "L" + STRING_CLASS_NAME + ";";
    }
    return "(" + params + ")V";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReportingMethod)) return false;

    ReportingMethod method = (ReportingMethod)o;

    if (argCount != method.argCount) return false;
    if (!exceptionClass.equals(method.exceptionClass)) return false;
    if (!descrPattern.equals(method.descrPattern)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = exceptionClass.hashCode();
    result = 31 * result + descrPattern.hashCode();
    result = 31 * result + argCount;
    return result;
  }

  void generateMethod(ClassVisitor cw, String name) {
    MethodVisitor mv = cw.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC | ACC_STATIC, name, getMethodDesc(), null, null);
    mv.visitTypeInsn(NEW, exceptionClass);
    mv.visitInsn(DUP);

    mv.visitLdcInsn(descrPattern);

    mv.visitLdcInsn(argCount);
    mv.visitTypeInsn(ANEWARRAY, OBJECT_CLASS_NAME);

    for (int i = 0; i < argCount; i++) {
      mv.visitInsn(DUP);
      mv.visitLdcInsn(i);
      mv.visitVarInsn(ALOAD, i);
      mv.visitInsn(AASTORE);
    }

    //noinspection SpellCheckingInspection
    mv.visitMethodInsn(INVOKESTATIC, STRING_CLASS_NAME, "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);

    mv.visitMethodInsn(INVOKESPECIAL, exceptionClass, CONSTRUCTOR_NAME, EXCEPTION_INIT_SIGNATURE, false);
    mv.visitInsn(ATHROW);

    mv.visitMaxs(0, 0);
  }
}
