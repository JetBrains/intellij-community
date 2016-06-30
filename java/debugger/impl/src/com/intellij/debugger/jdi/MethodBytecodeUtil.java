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
package com.intellij.debugger.jdi;

import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import org.jetbrains.org.objectweb.asm.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author egor
 */
public class MethodBytecodeUtil {
  private MethodBytecodeUtil() {
  }

  /**
   * Allows to use ASM MethodVisitor with jdi method bytecode
   */
  public static void visit(ClassType classType, Method method, MethodVisitor methodVisitor) {
    try {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(bos)) {
        dos.writeInt(0xCAFEBABE); // magic
        dos.writeInt(Opcodes.V1_8); // version
        dos.writeShort(classType.constantPoolCount()); // constant_pool_count
        dos.write(classType.constantPool()); // constant_pool

        // we generate and put code attribute right after the constant pool
        byte[] bytecodes = method.bytecodes();
        int codeSize = dos.size();
        dos.writeShort(0); // max_stack
        dos.writeShort(0); // max_locals
        dos.writeInt(bytecodes.length);  // code_length
        dos.write(bytecodes); // code
        dos.writeShort(0); // exception_table_length
        dos.writeShort(0); // attributes_count
        codeSize = dos.size() - codeSize;

        ClassReader clsReader = new ClassReader(bos.toByteArray());
        ClassWriter clsWriter = new ClassWriter(clsReader, 0);
        clsWriter.visit(Opcodes.V1_8,
                        Opcodes.ACC_PUBLIC,
                        classType.name(),
                        classType.signature(),
                        classType.superclass().name(),
                        classType.interfaces().stream().map(ReferenceType::name).toArray(String[]::new));
        MethodVisitor mv = clsWriter.visitMethod(Opcodes.ACC_PUBLIC, method.name(), method.signature(), method.signature(), null);
        mv.visitAttribute(createCode(clsReader, codeSize));

        new ClassReader(clsWriter.toByteArray()).accept(new ClassVisitor(Opcodes.ASM5) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return methodVisitor;
          }
        }, 0);
      }
    }
    catch (IOException ignored) {
    }
  }

  private static Attribute createCode(ClassReader cr, int len) {
    return new Attribute("Code") {
      @Override
      public Attribute read(ClassReader cr, int off, int len, char[] buf, int codeOff, Label[] labels) {
        return super.read(cr, off, len, buf, codeOff, labels);
      }
    }.read(cr, cr.header, len, null, 0, null);
  }

}
