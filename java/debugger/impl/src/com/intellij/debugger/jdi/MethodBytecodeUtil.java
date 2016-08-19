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

import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableConsumer;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author egor
 */
public class MethodBytecodeUtil {
  private MethodBytecodeUtil() {
  }

  /**
   * Allows to use ASM MethodVisitor with jdi method bytecode
   */
  public static void visit(ReferenceType classType, Method method, MethodVisitor methodVisitor) {
    visit(classType, method, method.bytecodes(), methodVisitor);
  }

  public static void visit(ReferenceType classType, Method method, long maxOffset, MethodVisitor methodVisitor) {
    // need to keep the size, otherwise labels array will not be initialized correctly
    byte[] originalBytecodes = method.bytecodes();
    byte[] bytecodes = new byte[originalBytecodes.length];
    System.arraycopy(originalBytecodes, 0, bytecodes, 0, (int)maxOffset);
    visit(classType, method, bytecodes, methodVisitor);
  }

  public static byte[] getConstantPool(ReferenceType type) {
    try {
      return type.constantPool();
    }
    catch (NullPointerException e) { // workaround for JDK bug 6822627
      ReflectionUtil.resetField(type, "constantPoolInfoGotten");
      return type.constantPool();
    }
  }

  private static void visit(ReferenceType type, Method method, byte[] bytecodes, MethodVisitor methodVisitor) {
    try {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(bos)) {
        dos.writeInt(0xCAFEBABE); // magic
        dos.writeInt(Opcodes.V1_8); // version
        dos.writeShort(type.constantPoolCount()); // constant_pool_count
        dos.write(getConstantPool(type)); // constant_pool
        dos.writeShort(0); //             access_flags;
        dos.writeShort(0); //             this_class;
        dos.writeShort(0); //             super_class;
        dos.writeShort(0); //             interfaces_count;
        dos.writeShort(0); //             fields_count;
        dos.writeShort(0); //             methods_count;
        dos.writeShort(0); //             attributes_count;

        ClassReader reader = new ClassReader(bos.toByteArray());
        ClassWriter writer = new ClassWriter(reader, 0);

        String superName = null;
        String[] interfaces = null;
        if (type instanceof ClassType) {
          ClassType classType = (ClassType)type;
          ClassType superClass = classType.superclass();
          superName = superClass != null ? superClass.name() : null;
          interfaces = classType.interfaces().stream().map(ReferenceType::name).toArray(String[]::new);
        }
        else if (type instanceof InterfaceType) {
          interfaces = ((InterfaceType)type).superinterfaces().stream().map(ReferenceType::name).toArray(String[]::new);
        }

        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, type.name(), type.signature(), superName, interfaces);
        Attribute bootstrapMethods = createBootstrapMethods(reader, writer);
        if (bootstrapMethods != null) {
          writer.visitAttribute(bootstrapMethods);
        }

        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method.name(), method.signature(), method.signature(), null);
        mv.visitAttribute(createCode(writer, method, bytecodes));

        new ClassReader(writer.toByteArray()).accept(new ClassVisitor(Opcodes.API_VERSION) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            assert name.equals(method.name());
            return methodVisitor;
          }
        }, 0);
      }
    }
    catch (IOException ignored) {
    }
  }

  @NotNull
  private static Attribute createAttribute(String name, ThrowableConsumer<DataOutputStream, IOException> generator) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(bos)) {
      dos.writeInt(0xCAFEBABE); // magic
      dos.writeInt(Opcodes.V1_8); // version
      dos.writeShort(0); // constant_pool_count

      // we generate and put attribute right after the constant pool
      int attributeSize = dos.size();
      generator.consume(dos);
      attributeSize = dos.size() - attributeSize;

      ClassReader cr = new ClassReader(bos.toByteArray());

      return new Attribute(name) {
        @Override
        public Attribute read(ClassReader cr, int off, int len, char[] buf, int codeOff, Label[] labels) {
          return super.read(cr, off, len, buf, codeOff, labels);
        }
      }.read(cr, cr.header, attributeSize, null, 0, null);
    }
  }

  @Nullable
  private static Attribute createBootstrapMethods(ClassReader classReader, ClassWriter classWriter) throws IOException {
    Set<Short> indys = new HashSet<>();
    // scan class pool for indy calls
    for (int i = 1; i < classReader.getItemCount(); i++) {
      int index = classReader.getItem(i);
      int tag = classReader.readByte(index - 1);
      switch (tag) {
        case 5: // ClassWriter.LONG
        case 6: // ClassWriter.DOUBLE
          ++i;
          break;
        case 18: // ClassWriter.INDY
          indys.add(classReader.readShort(index));
          //short methodIndex = classReader.readShort(index);
          short nameTypeIndex = classReader.readShort(index + 2);
      }
    }

    if (!indys.isEmpty()) {
      int dummyRef = classWriter.newHandle(Opcodes.H_INVOKESTATIC, "DummyOwner", "DummyMethod", "", false); // dummy for now
      return createAttribute("BootstrapMethods", dos -> {
        dos.writeShort(indys.size());
        for (Short indy : indys) {
          dos.writeShort(dummyRef); // bootstrap_method_ref
          dos.writeShort(0); // num_bootstrap_arguments
          //dos.writeShort(0); // bootstrap_arguments
        }
      });
    }

    return null;
  }

  @NotNull
  private static Attribute createCode(ClassWriter cw, Method method, byte[] bytecodes) throws IOException {
    return createAttribute("Code", dos -> {
      dos.writeShort(0); // max_stack
      dos.writeShort(0); // max_locals
      dos.writeInt(bytecodes.length);  // code_length
      dos.write(bytecodes); // code
      dos.writeShort(0); // exception_table_length
      List<Location> locations = getMethodLocations(method);
      if (!locations.isEmpty()) {
        dos.writeShort(1); // attributes_count
        dos.writeShort(cw.newUTF8("LineNumberTable"));
        dos.writeInt(2 * locations.size() + 2);
        dos.writeShort(locations.size());
        for (Location l : locations) {
          dos.writeShort((short)l.codeIndex());
          dos.writeShort(l.lineNumber());
        }
      }
      else {
        dos.writeShort(0); // attributes_count
      }
    });
  }

  @NotNull
  private static List<Location> getMethodLocations(Method method) {
    try {
      return method.allLineLocations();
    }
    catch (AbsentInformationException ignored) {
      return Collections.emptyList();
    }
  }

  private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");

  public static Type getVarInstructionType(int opcode) {
    switch (opcode) {
      case Opcodes.LLOAD:
      case Opcodes.LSTORE:
        return Type.LONG_TYPE;
      case Opcodes.DLOAD:
      case Opcodes.DSTORE:
        return Type.DOUBLE_TYPE;
      case Opcodes.FLOAD:
      case Opcodes.FSTORE:
        return Type.FLOAT_TYPE;
      case Opcodes.ILOAD:
      case Opcodes.ISTORE:
        return Type.INT_TYPE;
      default:
        // case Opcodes.ALOAD:
        // case Opcodes.ASTORE:
        // case RET:
        return OBJECT_TYPE;
    }
  }
}
