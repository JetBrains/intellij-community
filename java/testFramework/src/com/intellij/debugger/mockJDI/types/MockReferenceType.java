// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.mockJDI.MockLocation;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.members.MockConstructor;
import com.intellij.debugger.mockJDI.members.MockField;
import com.intellij.debugger.mockJDI.members.MockMethod;
import com.intellij.debugger.mockJDI.values.MockClassLoaderReference;
import com.intellij.debugger.mockJDI.values.MockValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class MockReferenceType extends MockType implements ReferenceType {
  private static final Logger LOG = Logger.getInstance(MockReferenceType.class);
  protected Class<?> myType;
  private List<String> mySourcePaths;
  private List<String> mySourceNames;
  private Map<Integer, List<Location>> myLine2Locations;
  private boolean mySMAPComputed;
  private SMAPInfo mySmapInfo;

  protected MockReferenceType(Class<?> type, final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
    myType = type;
    LOG.assertTrue(type == null || !type.isPrimitive());
  }

  @Override
  public String signature() {
    return "L" + name().replace('.', '/') + ";";
  }

  @Override
  public String name() {
    return myType.getName();
  }

  @Override
  public String genericSignature() {
    throw new UnsupportedOperationException("Not implemented: \"genericSignature\" in " + getClass().getName());
  }

  @Override
  public ClassLoaderReference classLoader() {
    return new MockClassLoaderReference(myVirtualMachine, myType.getClassLoader());
  }

  @Override
  public String sourceName() {
    final List<String> names = sourceNames(null);
    return names.isEmpty() ? null : names.get(0);
  }

  @Override
  public List<String> sourceNames(String string) {
    if (mySourceNames == null) {
      final List<String> paths = sourcePaths(string);
      mySourceNames = new ArrayList<>(paths.size());
      for (String path : paths) {
        final int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        mySourceNames.add(path.substring(i + 1));
      }
    }
    return mySourceNames;
  }

  private
  @Nullable
  SMAPInfo getSmapInfo() {
    if (!mySMAPComputed) {
      myVirtualMachine.getReader(myType).accept(new ClassVisitor(Opcodes.API_VERSION) {
        @Override
        public void visitSource(final String source, final String debug) {
          if (debug != null) {
            mySmapInfo = SMAPInfo.parse(new StringReader(debug));
          }
        }
      }, 0);
      mySMAPComputed = true;
    }
    return mySmapInfo;
  }

  @Override
  public List<String> sourcePaths(final String stratum) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<>();
      final SMAPInfo smapInfo = getSmapInfo();
      if (smapInfo == null) {
        final ClassReader reader = myVirtualMachine.getReader(myType);
        reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitSource(String source, String debug) {
            mySourcePaths.add(source);
          }
        }, 0);
      }
      else {
        for (SMAPInfo.FileInfo info : smapInfo.getStratum(stratum).getFileInfos()) {
          final String path = info.getPath();
          if (path != null) {
            mySourcePaths.add(path);
          }
        }
      }
    }
    return mySourcePaths;
  }

  @Override
  public String sourceDebugExtension() {
    throw new UnsupportedOperationException("Not implemented: \"sourceDebugExtension\" in " + getClass().getName());
  }

  @Override
  public boolean isStatic() {
    return Modifier.isStatic(myType.getModifiers());
  }

  @Override
  public boolean isAbstract() {
    return Modifier.isAbstract(myType.getModifiers());
  }

  @Override
  public boolean isFinal() {
    return Modifier.isFinal(myType.getModifiers());
  }

  @Override
  public boolean isPrepared() {
    return true;
  }

  @Override
  public boolean isVerified() {
    return true;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean failedToInitialize() {
    return false;
  }

  @Override
  public List<Field> fields() {
    ArrayList<Field> fields = new ArrayList<>();
    for (java.lang.reflect.Field field : myType.getDeclaredFields()) {
      fields.add(new MockField(field, myVirtualMachine));
    }
    return fields;
  }

  @Override
  public List<Field> visibleFields() {
    ArrayList<Field> fields = new ArrayList<>();
    for (java.lang.reflect.Field field : myType.getFields()) {
      fields.add(new MockField(field, myVirtualMachine));
    }
    return fields;
  }

  @Override
  public List<Field> allFields() {
    ArrayList<Field> fields = new ArrayList<>();
    List<ReferenceType> supers = getThisAndAllSupers();
    for (ReferenceType referenceType : supers) {
      fields.addAll(referenceType.fields());
    }
    return fields;
  }

  @Override
  public Field fieldByName(String string) {
    for (Field field : fields()) {
      if (field.name().equals(string)) return field;
    }
    return null;
  }

  @Override
  public List<Method> methods() {
    ArrayList<Method> methods = new ArrayList<>();
    for (Constructor constructor : myType.getDeclaredConstructors()) {
      methods.add(new MockConstructor(constructor, myVirtualMachine));
    }
    for (java.lang.reflect.Method method : myType.getDeclaredMethods()) {
      methods.add(new MockMethod(method, myVirtualMachine));
    }
    return methods;
  }

  @Override
  public List<Method> visibleMethods() {
    ArrayList<Method> methods = new ArrayList<>();
    for (Constructor constructor : myType.getConstructors()) {
      methods.add(new MockConstructor(constructor, myVirtualMachine));
    }
    for (java.lang.reflect.Method method : myType.getMethods()) {
      methods.add(new MockMethod(method, myVirtualMachine));
    }
    return methods;
  }

  @Override
  public List<Method> allMethods() {
    ArrayList<Method> methods = new ArrayList<>();
    List<ReferenceType> supers = getThisAndAllSupers();
    for (ReferenceType referenceType : supers) {
      methods.addAll(referenceType.methods());
    }
    return methods;
  }

  protected abstract List<ReferenceType> getThisAndAllSupers();

  @Override
  public List<Method> methodsByName(String string) {
    return ContainerUtil.filter(allMethods(), method -> method.name().equals(string));
  }

  @Override
  public List<Method> methodsByName(String string, String string1) {
    return ContainerUtil.filter(allMethods(), method -> method.name().equals(string) && method.signature().equals(string1));
  }

  @Override
  public List<ReferenceType> nestedTypes() {
    ArrayList<ReferenceType> referenceTypes = new ArrayList<>();
    for (Class aClass : myType.getDeclaredClasses()) {
      referenceTypes.add(myVirtualMachine.createReferenceType(aClass));
    }
    return referenceTypes;
  }

  @Override
  public Value getValue(Field field) {
    try {
      final java.lang.reflect.Field refField = ((MockField)field).getField();
      refField.setAccessible(true);
      return MockValue.createValue(refField.get(null), refField.getType(), myVirtualMachine);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<Field, Value> getValues(List<? extends Field> list) {
    throw new UnsupportedOperationException("Not implemented: \"getValues\" in " + getClass().getName());
  }

  @Override
  public ClassObjectReference classObject() {
    throw new UnsupportedOperationException("Not implemented: \"classObject\" in " + getClass().getName());
  }

  @Override
  public List<Location> allLineLocations() {
    throw new UnsupportedOperationException("'allLineLocations' not implemented in " + getClass().getName());
  }

  @Override
  public List<Location> allLineLocations(String string, String string1) {
    return allLineLocations();
  }

  @Override
  public List<Location> locationsOfLine(final String stratum, String string1, int line) {
    if (myLine2Locations == null) {
      myLine2Locations = new HashMap<>();
      final ClassReader reader = myVirtualMachine.getReader(myType);
      reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          Method method = DebuggerUtils.findMethod(MockReferenceType.this, name, desc);
          if (method == null) {
            return super.visitMethod(access, name, desc, signature, exceptions);
          }
          return new LineNumbersEvaluatingVisitor(stratum, method);
        }
      }, 0);
    }
    return myLine2Locations.get(line);
  }

  @Override
  public List<Location> locationsOfLine(int i) {
    return locationsOfLine(myVirtualMachine.getDefaultStratum(), null, i);
  }

  @Override
  public List<String> availableStrata() {
    return new ArrayList<>();
  }

  @Override
  public String defaultStratum() {
    throw new UnsupportedOperationException("Not implemented: \"defaultStratum\" in " + getClass().getName());
  }

  @Override
  public int compareTo(ReferenceType o) {
    throw new UnsupportedOperationException("Not implemented: \"compareTo\" in " + getClass().getName());
  }

  @Override
  public int modifiers() {
    return myType.getModifiers();
  }

  @Override
  public boolean isPrivate() {
    return Modifier.isPrivate(myType.getModifiers());
  }

  @Override
  public boolean isPackagePrivate() {
    return !isPrivate() && !isProtected() && !isPublic();
  }

  @Override
  public boolean isProtected() {
    return Modifier.isProtected(myType.getModifiers());
  }

  @Override
  public boolean isPublic() {
    return Modifier.isPublic(myType.getModifiers());
  }

  protected Class[] getAllInterfaces() {
    Set<Class<?>> interfaces = new HashSet<>();
    addInterfaces(interfaces, myType);
    return interfaces.toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  private static void addInterfaces(Set<Class<?>> interfaces, Class type) {
    for (Class aClass : type.getInterfaces()) {
      interfaces.add(aClass);
      addInterfaces(interfaces, aClass);
    }
  }

  public List<InterfaceType> allInterfaces() {
    ArrayList<InterfaceType> interfaceTypes = new ArrayList<>();
    for (Class<?> aClass : getAllInterfaces()) {
      interfaceTypes.add(myVirtualMachine.createInterfaceType(aClass));
    }
    return interfaceTypes;
  }

  private class LineNumbersEvaluatingVisitor extends MethodVisitor {
    private final String myStratum;
    private final Method myMethod;

    LineNumbersEvaluatingVisitor(final String stratum, final Method method) {
      super(Opcodes.API_VERSION);
      myStratum = stratum;
      myMethod = method;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      final SMAPInfo smapInfo = getSmapInfo();
      if (smapInfo != null) {
        final SMAPInfo.FileInfo[] infos = smapInfo.getStratum(myStratum).getFileInfos();
        for (SMAPInfo.FileInfo info : infos) {
          final int inputLine = info.getInputLine(line);
          if (inputLine != -1) {
            addLocation(new MockLocation(inputLine, -1, myMethod, info.getPath(), info.getName()));
          }
        }
      }
      else {
        addLocation(new MockLocation(line, -1, myMethod));
      }
    }

    private void addLocation(final MockLocation location) {
      final int line = location.lineNumber();
      List<Location> list = myLine2Locations.get(line);
      if (list == null) {
        list = new ArrayList<>();
        myLine2Locations.put(line, list);
      }
      list.add(location);
    }
  }
}
