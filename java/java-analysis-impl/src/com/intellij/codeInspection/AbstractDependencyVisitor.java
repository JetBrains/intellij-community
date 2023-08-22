// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractDependencyVisitor extends ClassVisitor {

  private static final Logger LOG = Logger.getInstance(AbstractDependencyVisitor.class);
  private static final Label LABEL = new Label();

  private final AnnotationDependencyVisitor myAnnotationVisitor = new AnnotationDependencyVisitor();
  private final DependencySignatureVisitor mySignatureVisitor = new DependencySignatureVisitor();
  private final DependencyFieldVisitor myFieldVisitor = new DependencyFieldVisitor();

  private String myCurrentClassName;

  protected AbstractDependencyVisitor() {
    super(Opcodes.API_VERSION);
  }

  protected abstract void addClassName(String name);

  public void processFile(final File file) {
    try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
      processStream(is);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public void processStream(InputStream is) throws IOException {
    ClassReader cr = new ClassReader(is) {
      @Override
      protected Label readLabel(int offset, Label[] labels) {
        if (offset >= labels.length) {
          // workaround for JDK8 javac bugs:
          // https://bugs.openjdk.org/browse/JDK-8144185
          // https://bugs.openjdk.org/browse/JDK-8187805
          // https://bugs.openjdk.org/browse/JDK-8191969
          return LABEL;
        }
        return super.readLabel(offset, labels);
      }
    };
    cr.accept(this, ClassReader.SKIP_FRAMES);
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myCurrentClassName = getSlotName(name);

    if (signature == null) {
      addName(superName);
      addNames(interfaces);
    }
    else {
      addSignature(signature);
    }
  }


  private final Map<String, String> mySlotNames = new HashMap<>();

  private String getSlotName(String name) {
    String result = mySlotNames.get(name);
    if (result == null) {
      result = name.replace("/", ".");
      final int idx = result.indexOf("$");
      if (idx >= 0) {
        result = result.substring(0, idx);
      }

      mySlotNames.put(name, result);
    }

    return result;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    addDesc(desc);
    return myAnnotationVisitor;
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    addDesc(desc);
    return myAnnotationVisitor;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    if (signature == null) {
      addDesc(desc);
    }
    else {
      addTypeSignature(signature);
    }
    if (value instanceof Type) addType((Type)value);
    return myFieldVisitor;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    // skip signature of synthetic methods created for lambda's and such
    if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
      if (signature == null) {
        addMethodDesc(desc);
      }
      else {
        addSignature(signature);
      }
      addNames(exceptions);
    }
    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
      return null;
    }
    return new DependencyMethodVisitor();
  }

  private class DependencyMethodVisitor extends MethodVisitor {

    private Label myFirstLabel = null;

    DependencyMethodVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public void visitLabel(Label label) {
      if (myFirstLabel == null) {
        myFirstLabel = label;
      }
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public void visitTypeInsn(int opcode, String desc) {
      if (Opcodes.NEW == opcode) return; // skip, reference to constructor will already be counted
      if (desc.charAt(0) == '[') {
        addDesc(desc);
      }
      else {
        addName(desc);
      }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      addName(owner);
      addDesc(desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      addName(owner);
      addMethodDesc(desc);
    }

    @Override
    public void visitLdcInsn(Object cst) {
      if (cst instanceof Type) addType((Type)cst);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
      addDesc(desc);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      addName(type);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      if (myFirstLabel == start) { // this or parameter
        return;
      }
      addTypeSignature(signature == null ? desc : signature);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      addDesc(desc);

      for (Object arg : bsmArgs) {
        if (arg instanceof Handle) {
          addHandle((Handle)arg);
        }
      }
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
                                                          TypePath typePath,
                                                          Label[] start,
                                                          Label[] end,
                                                          int[] index,
                                                          String desc,
                                                          boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

  }

  private class DependencyFieldVisitor extends FieldVisitor {

    DependencyFieldVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      addDesc(desc);
      return myAnnotationVisitor;
    }
  }

  private class AnnotationDependencyVisitor extends AnnotationVisitor {

    AnnotationDependencyVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public void visit(String name, Object value) {
      if (value instanceof Type) addType((Type)value);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      addDesc(desc);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      addDesc(desc);
      return this;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return this;
    }
  }

  private class DependencySignatureVisitor extends SignatureVisitor {

    DependencySignatureVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public void visitClassType(String name) {
      addName(name);
    }

    @Override
    public void visitInnerClassType(String name) {
      addName(name);
    }
  }

  private void addName(String name) {
    if (name == null) return;

    name = getSlotName(name);

    if (name.equals(myCurrentClassName)) return;

    addClassName(name);
  }

  private void addNames(String[] names) {
    for (int i = 0; names != null && i < names.length; i++) {
      addName(names[i]);
    }
  }

  private void addDesc(String desc) {
    addType(Type.getType(desc));
  }

  private void addHandle(Handle h) {
    addName(h.getOwner());
    int tag = h.getTag();
    String desc = h.getDesc();
    if (tag == Opcodes.H_INVOKEVIRTUAL ||
        tag == Opcodes.H_INVOKESTATIC || 
        tag == Opcodes.H_INVOKESPECIAL ||
        tag == Opcodes.H_NEWINVOKESPECIAL || 
        tag == Opcodes.H_INVOKEINTERFACE) {
      addMethodDesc(desc);
    }
    else {
      addDesc(desc);
    }
  }

  private void addMethodDesc(String desc) {
    addType(Type.getReturnType(desc));
    Type[] types = Type.getArgumentTypes(desc);
    for (Type type : types) {
      addType(type);
    }
  }

  private void addType(Type t) {
    switch (t.getSort()) {
      case Type.ARRAY -> addType(t.getElementType());
      case Type.OBJECT -> addName(t.getClassName().replace('.', '/'));
      case Type.METHOD -> addMethodDesc(t.getDescriptor());
    }
  }

  private void addSignature(String signature) {
    if (signature != null) new SignatureReader(signature).accept(mySignatureVisitor);
  }

  private void addTypeSignature(String signature) {
    if (signature != null) new SignatureReader(signature).acceptType(mySignatureVisitor);
  }

  @NlsSafe
  public String getCurrentClassName() {
    return myCurrentClassName;
  }
}
