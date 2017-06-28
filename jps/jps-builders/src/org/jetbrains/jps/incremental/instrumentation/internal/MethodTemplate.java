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

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

class MethodTemplate {
  private final byte[] myBytecode;

  MethodTemplate(Class aClass) {
    try {
      myBytecode = readBytecodeOf(aClass);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] readBytecodeOf(Class aClass) throws IOException {
    InputStream stream = aClass.getResourceAsStream(aClass.getSimpleName() + ".class");
    if (stream == null) {
      throw new IllegalArgumentException("Cannot read class bytecode: " + aClass);
    }
    try {
      return FileUtil.loadBytes(stream);
    }
    finally {
      stream.close();
    }
  }

  public void write(ClassVisitor writer, int accessModifier, String methodName) {
    ClassReader reader = new ClassReader(myBytecode);

    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return "<init>".equals(name) ? null : writer.visitMethod(access | accessModifier, methodName, desc, signature, exceptions);
      }
    }, 0);
  }
}
