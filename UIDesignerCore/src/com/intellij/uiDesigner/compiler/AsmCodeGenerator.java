/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2005
 * Time: 12:58:35
 * To change this template use File | Settings | File Templates.
 */
public class AsmCodeGenerator {
  private LwRootContainer myRootContainer;
  private ClassLoader myLoader;
  private LayoutCodeGenerator myLayoutCodeGenerator;
  private ArrayList myErrors;
  private ArrayList myWarnings;

  private static final String CONSTRUCTOR_NAME = "<init>";
  private String myClassToBind;
  private byte[] myPatchedData;

  public AsmCodeGenerator(final LwRootContainer rootContainer,
                          final ClassLoader loader,
                          final LayoutCodeGenerator layoutCodeGenerator) {
    if (loader == null){
      throw new IllegalArgumentException("loader cannot be null");
    }
    if (rootContainer == null){
      throw new IllegalArgumentException("rootContainer cannot be null");
    }
    myRootContainer = rootContainer;
    myLoader = loader;
    myLayoutCodeGenerator = layoutCodeGenerator;

    myErrors = new ArrayList();
    myWarnings = new ArrayList();
  }

  public void patchFile(final File classFile) {
    if (!classFile.exists()) {
      myErrors.add("Class to bind does not exist: " + myRootContainer.getClassToBind());
      return;
    }

    FileInputStream fis = null;
    try {
      byte[] patchedData;
      fis = new FileInputStream(classFile);
      try {
        patchedData = patchClass(fis);
        if (patchedData == null) {
          return;
        }
      }
      finally {
        fis.close();
      }

      FileOutputStream fos = new FileOutputStream(classFile);
      try {
        fos.write(patchedData);
      }
      finally {
        fos.close();
      }
    }
    catch (IOException e) {
      myErrors.add("Cannot read or write class file " + classFile.getPath() + ": " + e.toString());
      return;
    }
  }

  public byte[] patchClass(InputStream classStream) {
    myClassToBind = myRootContainer.getClassToBind();
    if (myClassToBind == null){
      myWarnings.add("No class to bind specified");
      return null;
    }

    if (myRootContainer.getComponentCount() != 1) {
      myErrors.add("There should be only one component at the top level");
      return null;
    }

    if (FormByteCodeGenerator.containsNotEmptyPanelsWithXYLayout((LwComponent)myRootContainer.getComponent(0))) {
      myErrors.add("There are non empty panels with XY layout. Please lay them out in a grid.");
      return null;
    }

    ClassReader reader = null;
    try {
      reader = new ClassReader(classStream);
    }
    catch (IOException e) {
      myErrors.add("Error reading class data stream");
      return null;
    }
    ClassWriter cw = new ClassWriter(true);
    reader.accept(new FormClassVisitor(cw), false);
    myPatchedData = cw.toByteArray();
    return myPatchedData;
  }

  public String[] getErrors() {
    return (String[])myErrors.toArray(new String[myErrors.size()]);
  }

  public String[] getWarnings() {
    return (String[])myWarnings.toArray(new String[myWarnings.size()]);
  }

  public byte[] getPatchedData() {
    return myPatchedData;
  }

  private class FormClassVisitor extends ClassAdapter {
    private String myClassName;
    private String mySuperName;
    private Map myFieldDescMap = new HashMap();
    private Map myFieldAccessMap = new HashMap();

    public FormClassVisitor(final ClassVisitor cv) {
      super(cv);
    }

    public void visit(final int version,
                      final int access,
                      final String name,
                      final String signature,
                      final String superName,
                      final String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      myClassName = name;
      mySuperName = superName;
    }

    public MethodVisitor visitMethod(final int access,
                                     final String name,
                                     final String desc,
                                     final String signature,
                                     final String[] exceptions) {

      if (name.equals(CodeGenerator.SETUP_METHOD_NAME)) {
        return null;
      }
      final MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
      if (name.equals(CONSTRUCTOR_NAME)) {
        return new FormConstructorVisitor(methodVisitor, myClassName, mySuperName);
      }
      return methodVisitor;
    }

    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
      myFieldDescMap.put(name, desc);
      myFieldAccessMap.put(name, new Integer(access));
      return super.visitField(access, name, desc, signature, value);
    }

    public void visitEnd() {
      Method method = Method.getMethod("void " + CodeGenerator.SETUP_METHOD_NAME + " ()");
      GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, cv);
      buildSetupMethod(adapter);
      super.visitEnd();
    }

    private void buildSetupMethod(final GeneratorAdapter generator) {
      try {
        generateSetupCodeForComponent((LwComponent)myRootContainer.getComponent(0), generator);
      }
      catch (CodeGenerationException e) {
        myErrors.add(e.getMessage());
      }
      generator.returnValue();
      generator.endMethod();
    }

    private void generateSetupCodeForComponent(final LwComponent lwComponent,
                                               final GeneratorAdapter generator) throws CodeGenerationException {
      Class componentClass = FormByteCodeGenerator.getComponentClass(lwComponent, myLoader);
      final Type componentType = Type.getType(componentClass);
      final int componentLocal = generator.newLocal(componentType);
      generator.newInstance(componentType);
      generator.dup();
      generator.invokeConstructor(componentType, Method.getMethod("void <init>()"));
      generator.storeLocal(componentLocal);

      generateFieldBinding(lwComponent, componentClass, generator, componentLocal);

      myLayoutCodeGenerator.generateContainerLayout(lwComponent, generator, componentLocal);
    }

    private void generateFieldBinding(final LwComponent lwComponent,
                                      final Class componentClass,
                                      final GeneratorAdapter generator,
                                      final int componentLocal) throws CodeGenerationException {
      final String binding = lwComponent.getBinding();
      if (binding != null) {
        if (!myFieldDescMap.containsKey(binding)) {
          throw new CodeGenerationException("Cannot bind: field does not exist: " + myClassToBind + "." + binding);
        }

        Integer access = (Integer) myFieldAccessMap.get(binding);
        if ((access.intValue() & Opcodes.ACC_STATIC) != 0) {
          throw new CodeGenerationException("Cannot bind: field is static: " + myClassToBind + "." + binding);
        }
        if ((access.intValue() & Opcodes.ACC_FINAL) != 0) {
          throw new CodeGenerationException("Cannot bind: field is final: " + myClassToBind + "." + binding);
        }

        final Type fieldType = Type.getType((String)myFieldDescMap.get(binding));
        if (fieldType.getSort() != Type.OBJECT) {
          throw new CodeGenerationException("Cannot bind: field is of primitive type: " + myClassToBind + "." + binding);
        }

        Class fieldClass;
        try {
          fieldClass = myLoader.loadClass(fieldType.getClassName());
        }
        catch (ClassNotFoundException e) {
          throw new CodeGenerationException("Class not found: " + fieldType.getClassName());
        }
        if (!fieldClass.isAssignableFrom(componentClass)) {
          throw new CodeGenerationException("Cannot bind: Incompatible types. Cannot assign " + componentClass.getName() + " to field " + myClassToBind + "." + binding);
        }

        generator.loadThis();
        generator.loadLocal(componentLocal);
        generator.putField(Type.getType("L" + myClassName + ";"), binding, fieldType);
      }
    }
  }

  private static class FormConstructorVisitor extends MethodAdapter {
    private final String myClassName;
    private final String mySuperName;
    private boolean callsSelfConstructor = false;

    public FormConstructorVisitor(final MethodVisitor mv, final String className, final String superName) {
      super(mv);
      myClassName = className;
      mySuperName = superName;
    }

    public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
      super.visitMethodInsn(opcode, owner, name, desc);
      if (opcode == Opcodes.INVOKESPECIAL && name.equals(CONSTRUCTOR_NAME)) {
        if (owner.equals(myClassName)) {
          callsSelfConstructor = true;
          return;
        }
        if (owner.equals(mySuperName) && !callsSelfConstructor) {
          mv.visitVarInsn(Opcodes.ALOAD, 0);
          mv.visitMethodInsn(Opcodes.INVOKESPECIAL, myClassName, CodeGenerator.SETUP_METHOD_NAME, "()V");
        }
      }
    }
  }
}
