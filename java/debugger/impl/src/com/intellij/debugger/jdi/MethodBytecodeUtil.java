// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.Patches;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MethodBytecodeUtil {
  private MethodBytecodeUtil() { }

  /**
   * Allows to use ASM MethodVisitor with JDI method bytecode
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

    BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(bytes)) {
      writeClassHeader(dos, type.constantPoolCount(), getConstantPool(type));
    }
    catch (IOException e) { throw new RuntimeException(e); }
    ClassReader reader = new ClassReader(bytes.getInternalBuffer(), 0, bytes.size());

    ClassWriter writer = new ClassWriter(reader, 0);
    String superName = null;
    String[] interfaces = null;
    if (type instanceof ClassType) {
      ClassType superClass = ((ClassType)type).superclass();
      superName = superClass != null ? superClass.name() : null;
      interfaces = ((ClassType)type).interfaces().stream().map(ReferenceType::name).toArray(String[]::new);
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

  private static void writeClassHeader(DataOutputStream dos, int constantPoolCount, byte[] constantPool) throws IOException {
    dos.writeInt(0xCAFEBABE);
    dos.writeInt(Opcodes.V1_8);
    dos.writeShort(constantPoolCount + 1);
    dos.write(constantPool);
    byte[] utfStr = "BootstrapMethods".getBytes(StandardCharsets.UTF_8);
    dos.writeByte(1);  // name string for the fake "BootstrapMethods" attribute
    dos.writeShort(utfStr.length);
    dos.write(utfStr);
    dos.writeShort(0);  // access_flags
    dos.writeShort(0);  // this_class
    dos.writeShort(0);  // super_class
    dos.writeShort(0);  // interfaces_count
    dos.writeShort(0);  // fields_count
    dos.writeShort(0);  // methods_count
    dos.writeShort(1);  // attributes_count
    dos.writeShort(constantPoolCount);  // fake "BootstrapMethods" attribute
    dos.writeInt(0);
  }

  private static @Nullable Attribute createBootstrapMethods(ClassReader classReader, ClassWriter classWriter) {
    Set<Short> bootstrapMethods = new HashSet<>();
    // scan class pool for indy calls
    for (int i = 1; i < classReader.getItemCount(); i++) {
      int index = classReader.getItem(i);
      int tag = classReader.readByte(index - 1);
      switch (tag) {
        case 5:  // Symbol.CONSTANT_LONG_TAG
        case 6:  // Symbol.CONSTANT_DOUBLE_TAG
          //noinspection AssignmentToForLoopParameter
          ++i;
          break;
        case 18:  // Symbol.CONSTANT_INVOKE_DYNAMIC_TAG
          bootstrapMethods.add(classReader.readShort(index));
      }
    }

    if (!bootstrapMethods.isEmpty()) {
      int dummyRef = classWriter.newHandle(Opcodes.H_INVOKESTATIC, "DummyOwner", "DummyMethod", "", false); // dummy for now
      return createAttribute("BootstrapMethods", dos -> {
        dos.writeShort(bootstrapMethods.size());
        for (int i = 0; i < bootstrapMethods.size(); i++) {
          dos.writeShort(dummyRef); // bootstrap_method_ref
          dos.writeShort(0); // num_bootstrap_arguments
        }
      });
    }

    return null;
  }

  private static Attribute createCode(ClassWriter cw, Method method, byte[] bytecodes, boolean withLineNumbers) {
    return createAttribute("Code", dos -> {
      dos.writeShort(0); // max_stack
      dos.writeShort(0); // max_locals
      dos.writeInt(bytecodes.length);  // code_length
      dos.write(bytecodes); // code
      dos.writeShort(0); // exception_table_length
      List<Location> locations = withLineNumbers ? DebuggerUtilsEx.allLineLocations(method) : null;
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

  private static Attribute createAttribute(String name, ThrowableConsumer<DataOutputStream, IOException> generator) {
    BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
    int start, end;

    try (DataOutputStream dos = new DataOutputStream(bytes)) {
      writeClassHeader(dos, 0, ArrayUtil.EMPTY_BYTE_ARRAY);
      start = dos.size();
      generator.consume(dos);
      end = dos.size();
    }
    catch (IOException e) { throw new RuntimeException(e); }

    ClassReader reader = new ClassReader(bytes.getInternalBuffer(), 0, bytes.size());
    return new Attribute(name) {
      public Attribute read() {
        return read(reader, start, end - start, null, 0, null);
      }
    }.read();
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

  public static List<Location> removeSameLineLocations(@NotNull List<Location> locations) {
    if (locations.size() < 2) {
      return locations;
    }
    MultiMap<Method, Location> byMethod = new MultiMap<>();
    for (Location location : locations) {
      byMethod.putValue(location.method(), location);
    }
    List<Location> res = new ArrayList<>();
    for (Map.Entry<Method, Collection<Location>> entry : byMethod.entrySet()) {
      res.addAll(removeMethodSameLineLocations(entry.getKey(), (List<Location>)entry.getValue()));
    }
    return res;
  }

  private static Collection<Location> removeMethodSameLineLocations(@NotNull Method method, @NotNull List<Location> locations) {
    int locationsSize = locations.size();
    if (locationsSize < 2) {
      return locations;
    }

    if (!method.declaringType().virtualMachine().canGetConstantPool()) {
      return locations;
    }

    int lineNumber = locations.get(0).lineNumber();
    List<Boolean> mask = new ArrayList<>(locationsSize);
    visit(method, new MethodVisitor(Opcodes.API_VERSION) {
      boolean myNewBlock = true;

      @Override
      public void visitLineNumber(int line, Label start) {
        if (lineNumber == line) {
          mask.add(myNewBlock);
          myNewBlock = false;
        }
      }

      @Override
      public void visitInsn(int opcode) {
        if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
          myNewBlock = true;
        }
      }

      @Override
      public void visitJumpInsn(int opcode, Label label) {
        myNewBlock = true;
      }
    }, true);

    if (mask.size() == locationsSize) {
      locations.sort(Comparator.comparing(Location::codeIndex));
      List<Location> res = new ArrayList<>(locationsSize);
      int pos = 0;
      for (Location location : locations) {
        if (mask.get(pos++)) {
          res.add(location);
        }
      }
      return res;
    }

    return locations;
  }
}