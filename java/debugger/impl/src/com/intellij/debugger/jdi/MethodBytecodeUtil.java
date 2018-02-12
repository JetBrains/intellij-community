// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.Patches;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
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
  public static void visit(Method method, MethodVisitor methodVisitor, boolean withLineNumbers) {
    visit(method, method.bytecodes(), methodVisitor, withLineNumbers);
  }

  public static void visit(Method method, long maxOffset, MethodVisitor methodVisitor, boolean withLineNumbers) {
    if (maxOffset > 0) {
      // need to keep the size, otherwise labels array will not be initialized correctly
      byte[] originalBytecodes = method.bytecodes();
      byte[] bytecodes = originalBytecodes;
      if (maxOffset < originalBytecodes.length) {
        bytecodes = new byte[originalBytecodes.length];
        System.arraycopy(originalBytecodes, 0, bytecodes, 0, (int)maxOffset);
      }
      visit(method, bytecodes, methodVisitor, withLineNumbers);
    }
  }

  public static byte[] getConstantPool(ReferenceType type) {
    if (Patches.JDK_BUG_ID_6822627) {
      try {
        return type.constantPool();
      }
      catch (NullPointerException e) { // workaround for JDK bug 6822627
        ReflectionUtil.resetField(type, "constantPoolInfoGotten");
        return type.constantPool();
      }
    }
    else {
      return type.constantPool();
    }
  }

  private static void visit(Method method, byte[] bytecodes, MethodVisitor methodVisitor, boolean withLineNumbers) {
    ReferenceType type = method.declaringType();
    try {
      byte[] constantPool = getConstantPool(type);
      try (ByteArrayBuilderOutputStream bos = new ByteArrayBuilderOutputStream(constantPool.length + 24);
           DataOutputStream dos = new DataOutputStream(bos)) {

        dos.writeInt(0xCAFEBABE); // magic
        dos.writeInt(Opcodes.V1_8); // version
        dos.writeShort(type.constantPoolCount()); // constant_pool_count
        dos.write(constantPool); // constant_pool
        dos.writeShort(0); //             access_flags;
        dos.writeShort(0); //             this_class;
        dos.writeShort(0); //             super_class;
        dos.writeShort(0); //             interfaces_count;
        dos.writeShort(0); //             fields_count;
        dos.writeShort(0); //             methods_count;
        dos.writeShort(0); //             attributes_count;

        ClassReader reader = new ClassReader(bos.getBuffer());
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
        mv.visitAttribute(createCode(writer, method, bytecodes, withLineNumbers));

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
  private static Attribute createCode(ClassWriter cw, Method method, byte[] bytecodes, boolean withLineNumbers) throws IOException {
    return createAttribute("Code", dos -> {
      dos.writeShort(0); // max_stack
      dos.writeShort(0); // max_locals
      dos.writeInt(bytecodes.length);  // code_length
      dos.write(bytecodes); // code
      dos.writeShort(0); // exception_table_length
      List<Location> locations = withLineNumbers ? DebuggerUtilsEx.allLineLocations(method) : Collections.emptyList();
      if (!ContainerUtil.isEmpty(locations)) {
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

  @Nullable
  public static Method getLambdaMethod(ReferenceType clsType, @NotNull ClassesByNameProvider classesByName) {
    Ref<Method> methodRef = Ref.create();
    if (DebuggerUtilsEx.isLambdaClassName(clsType.name())) {
      List<Method> applicableMethods = ContainerUtil.filter(clsType.methods(), m -> m.isPublic() && !m.isBridge());
      if (applicableMethods.size() == 1) {
        visit(applicableMethods.get(0), new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            ReferenceType cls = ContainerUtil.getFirstItem(classesByName.get(owner));
            if (cls != null) {
              Method method = DebuggerUtils.findMethod(cls, name, desc);
              if (method != null) {
                methodRef.setIfNull(method);
              }
            }
          }
        }, false);
      }
    }
    return methodRef.get();
  }

  @Nullable
  public static Method getBridgeTargetMethod(Method method, @NotNull ClassesByNameProvider classesByName) {
    Ref<Method> methodRef = Ref.create();
    if (method.isBridge()) {
      visit(method, new MethodVisitor(Opcodes.API_VERSION) {
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
          if ("java/lang/AbstractMethodError".equals(owner)) {
            return;
          }
          ReferenceType declaringType = method.declaringType();
          ReferenceType cls;
          owner = owner.replace("/", ".");
          if (declaringType.name().equals(owner)) {
            cls = declaringType;
          }
          else {
            cls = ContainerUtil.getFirstItem(classesByName.get(owner));
          }
          if (cls != null) {
            Method method = DebuggerUtils.findMethod(cls, name, desc);
            if (method != null) {
              methodRef.setIfNull(method);
            }
          }
        }
      }, false);
    }
    return methodRef.get();
  }

  private static class ByteArrayBuilderOutputStream extends ByteArrayOutputStream {
    public ByteArrayBuilderOutputStream(int size) {
      super(size);
    }

    byte[] getBuffer() {
      assert buf.length == count : "Buffer is not fully filled";
      return buf;
    }
  }
}
