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

import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.shared.BorderType;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;
import javax.swing.border.Border;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class AsmCodeGenerator {
  private LwRootContainer myRootContainer;
  private ClassLoader myLoader;
  private LayoutCodeGenerator myLayoutCodeGenerator;
  private ArrayList myErrors;
  private ArrayList myWarnings;

  private Map myIdToLocalMap = new HashMap();

  private static final String CONSTRUCTOR_NAME = "<init>";
  private String myClassToBind;
  private byte[] myPatchedData;

  private static Map myComponentLayoutCodeGenerators = new HashMap();
  private static Map myPropertyCodeGenerators = new HashMap();
  public static final String SETUP_METHOD_NAME = "$$$setupUI$$$";

  static {
    myComponentLayoutCodeGenerators.put(LwSplitPane.class, new SplitPaneLayoutCodeGenerator());
    myComponentLayoutCodeGenerators.put(LwTabbedPane.class, new TabbedPaneLayoutCodeGenerator());
    myComponentLayoutCodeGenerators.put(LwScrollPane.class, new ScrollPaneLayoutCodeGenerator());

    myPropertyCodeGenerators.put("java.lang.String", new StringPropertyCodeGenerator());
    myPropertyCodeGenerators.put("java.awt.Dimension", new DimensionPropertyCodeGenerator());
    myPropertyCodeGenerators.put("java.awt.Insets", new InsetsPropertyCodeGenerator());
    myPropertyCodeGenerators.put("java.awt.Rectangle", new RectanglePropertyCodeGenerator());
    myPropertyCodeGenerators.put("java.awt.Color", new ColorPropertyCodeGenerator());
  }

  public AsmCodeGenerator(final LwRootContainer rootContainer,
                          final ClassLoader loader) {
    if (loader == null){
      throw new IllegalArgumentException("loader cannot be null");
    }
    if (rootContainer == null){
      throw new IllegalArgumentException("rootContainer cannot be null");
    }
    myRootContainer = rootContainer;
    myLoader = loader;

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

    if (containsNotEmptyPanelsWithXYLayout((LwComponent)myRootContainer.getComponent(0))) {
      myErrors.add("There are non empty panels with XY layout. Please lay them out in a grid.");
      return null;
    }

    if ("GridBagLayout".equals(myRootContainer.getLayoutManager())) {
      myLayoutCodeGenerator = new GridBagLayoutCodeGenerator();
    }
    else {
      myLayoutCodeGenerator = new GridLayoutCodeGenerator();
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

  static void pushPropValue(GeneratorAdapter generator, String propertyClass, Object value) {
    PropertyCodeGenerator codeGen = (PropertyCodeGenerator)myPropertyCodeGenerators.get(propertyClass);
    if (codeGen == null) {
      throw new RuntimeException("Unknown property class " + propertyClass);
    }
    codeGen.generatePushValue(generator, value);
  }

  static boolean containsNotEmptyPanelsWithXYLayout(final LwComponent component) {
    if (!(component instanceof LwContainer)) {
      return false;
    }
    final LwContainer container = (LwContainer)component;
    if (container.getComponentCount() == 0){
      return false;
    }
    if (container.isXY()){
      return true;
    }
    for (int i=0; i < container.getComponentCount(); i++){
      if (containsNotEmptyPanelsWithXYLayout((LwComponent)container.getComponent(i))) {
        return true;
      }
    }
    return false;
  }

  static Class getComponentClass(String className, final ClassLoader classLoader) throws CodeGenerationException{
    try {
      return Class.forName(className, false, classLoader);
    }
    catch (ClassNotFoundException e) {
      throw new CodeGenerationException("Class not found: " + className);
    }
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

      if (name.equals(SETUP_METHOD_NAME)) {
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
      Method method = Method.getMethod("void " + SETUP_METHOD_NAME + " ()");
      GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, cv);
      buildSetupMethod(adapter);
      super.visitEnd();
    }

    private void buildSetupMethod(final GeneratorAdapter generator) {
      try {
        final LwComponent topComponent = (LwComponent)myRootContainer.getComponent(0);
        generateSetupCodeForComponent(topComponent, generator, -1);
        generateComponentReferenceProperties(topComponent, generator);
      }
      catch (CodeGenerationException e) {
        myErrors.add(e.getMessage());
      }
      generator.returnValue();
      generator.endMethod();
    }

    private void generateSetupCodeForComponent(final LwComponent lwComponent,
                                               final GeneratorAdapter generator,
                                               final int parentLocal) throws CodeGenerationException {
      final String className = myLayoutCodeGenerator.mapComponentClass(lwComponent.getComponentClassName());
      Class componentClass = getComponentClass(className, myLoader);
      final Type componentType = Type.getType(componentClass);
      final int componentLocal = generator.newLocal(componentType);

      myIdToLocalMap.put(lwComponent.getId(), new Integer(componentLocal));

      generator.newInstance(componentType);
      generator.dup();
      generator.invokeConstructor(componentType, Method.getMethod("void <init>()"));
      generator.storeLocal(componentLocal);

      generateFieldBinding(lwComponent, componentClass, generator, componentLocal);

      getComponentCodeGenerator(lwComponent).generateContainerLayout(lwComponent, generator, componentLocal);

      generateComponentProperties(lwComponent, componentType, generator, componentLocal);

      // add component to parent
      if (!(lwComponent.getParent() instanceof LwRootContainer)) {
        getComponentCodeGenerator(lwComponent.getParent()).generateComponentLayout(lwComponent, generator, componentLocal, parentLocal);
      }

      if (lwComponent instanceof LwContainer) {
        LwContainer container = (LwContainer) lwComponent;

        generateBorder(container, generator, componentLocal);

        for(int i=0; i<container.getComponentCount(); i++) {
          generateSetupCodeForComponent((LwComponent) container.getComponent(i), generator, componentLocal);
        }
      }
    }

    private LayoutCodeGenerator getComponentCodeGenerator(final LwComponent lwComponent) {
      LayoutCodeGenerator generator = (LayoutCodeGenerator) myComponentLayoutCodeGenerators.get(lwComponent.getClass());
      if (generator != null) {
        return generator;
      }
      return myLayoutCodeGenerator;
    }

    private void generateComponentProperties(final LwComponent lwComponent,
                                             final Type componentType,
                                             final GeneratorAdapter generator,
                                             final int componentLocal) throws CodeGenerationException {
      final Class componentClass = getComponentClass(lwComponent.getComponentClassName(), myLoader);
      // introspected properties
      final LwIntrospectedProperty[] introspectedProperties = lwComponent.getAssignedIntrospectedProperties();
      for (int i = 0; i < introspectedProperties.length; i++) {
        final LwIntrospectedProperty property = introspectedProperties[i];
        if (property instanceof LwIntroComponentProperty) {
          continue;
        }
        final String propertyClass = property.getPropertyClassName();
        final PropertyCodeGenerator propGen = (PropertyCodeGenerator) myPropertyCodeGenerators.get(propertyClass);


        if (propGen != null && propGen.generateCustomSetValue(lwComponent, componentClass, property,
                                                              generator, componentLocal)) {
          continue;
        }

        generator.loadLocal(componentLocal);

        Object value = lwComponent.getPropertyValue(property);
        Type setterArgType;
        if (propertyClass.equals(Integer.class.getName())) {
          generator.push(((Integer) value).intValue());
          setterArgType = Type.INT_TYPE;
        }
        else if (propertyClass.equals(Boolean.class.getName())) {
          generator.push(((Boolean) value).booleanValue());
          setterArgType = Type.BOOLEAN_TYPE;
        }
        else if (propertyClass.equals(Double.class.getName())) {
          generator.push(((Double) value).doubleValue());
          setterArgType = Type.DOUBLE_TYPE;
        }
        else {
          if (propGen == null) {
            continue;
          }
          propGen.generatePushValue(generator, value);
          setterArgType = getSetterArgType(property);
        }

        generator.invokeVirtual(componentType, new Method(property.getWriteMethodName(),
                                                          Type.VOID_TYPE, new Type[] { setterArgType } ));
      }
    }

    private Type getSetterArgType(final LwIntrospectedProperty property) {
      return Type.getType("L" + property.getPropertyClassName().replace('.', '/') + ";");
    }

    private void generateComponentReferenceProperties(final LwComponent component,
                                                      final GeneratorAdapter generator) throws CodeGenerationException {
      int componentLocal = ((Integer) myIdToLocalMap.get(component.getId())).intValue();
      Class componentClass = getComponentClass(myLayoutCodeGenerator.mapComponentClass(component.getComponentClassName()), myLoader);
      final Type componentType = Type.getType(componentClass);

      final LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();
      for (int i = 0; i < introspectedProperties.length; i++) {
        final LwIntrospectedProperty property = introspectedProperties[i];
        if (property instanceof LwIntroComponentProperty) {
          String targetId = (String) component.getPropertyValue(property);
          if (targetId != null && targetId.length() > 0) {
            int targetLocal = ((Integer) myIdToLocalMap.get(targetId)).intValue();
            generator.loadLocal(componentLocal);
            generator.loadLocal(targetLocal);
            generator.invokeVirtual(componentType,
                                    new Method(property.getWriteMethodName(),
                                               Type.VOID_TYPE, new Type[] { getSetterArgType(property) } ));
          }
        }
      }

      if (component instanceof LwContainer) {
        LwContainer container = (LwContainer) component;

        for(int i=0; i<container.getComponentCount(); i++) {
          generateComponentReferenceProperties((LwComponent) container.getComponent(i), generator);
        }
      }
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

    private void generateBorder(final LwContainer container, final GeneratorAdapter generator, final int componentLocal) {
      final BorderType borderType = container.getBorderType();
      final StringDescriptor borderTitle = container.getBorderTitle();
      final String borderFactoryMethodName = borderType.getBorderFactoryMethodName();

      final boolean borderNone = borderType.equals(BorderType.NONE);
      if (!borderNone || borderTitle != null) {
        // object to invoke setBorder
        generator.loadLocal(componentLocal);

        if (!borderNone) {
          generator.invokeStatic(Type.getType(BorderFactory.class),
                                 new Method(borderFactoryMethodName, Type.getType(Border.class), new Type[0]));
          AsmCodeGenerator.pushPropValue(generator, "java.lang.String", borderTitle);
          // use BorderFactory.createTitledBorder(Border, String)
          generator.invokeStatic(Type.getType(BorderFactory.class),
                                 Method.getMethod("javax.swing.border.TitledBorder createTitledBorder(javax.swing.border.Border,java.lang.String)"));
        }
        else {
          // use BorderFactory.createTitledBorder(String)
          AsmCodeGenerator.pushPropValue(generator, "java.lang.String", borderTitle);
          // use BorderFactory.createTitledBorder(Border, String)
          generator.invokeStatic(Type.getType(BorderFactory.class),
                                 Method.getMethod("javax.swing.border.TitledBorder createTitledBorder(java.lang.String)"));
        }

        // set border
        generator.invokeVirtual(Type.getType(JComponent.class),
                                Method.getMethod("void setBorder(javax.swing.border.Border)"));
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
          mv.visitMethodInsn(Opcodes.INVOKESPECIAL, myClassName, SETUP_METHOD_NAME, "()V");
        }
      }
    }
  }
}
