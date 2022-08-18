/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockArrayType extends MockReferenceType implements ArrayType {
  private final MockType myComponentType;

  public MockArrayType(MockType componentType) {
    super(null, componentType.virtualMachine());
    myComponentType = componentType;
  }

  @Override
  public ArrayReference newInstance(int i) {
    throw new UnsupportedOperationException(); 
  }

  @Override
  public String signature() {
    return "["+myComponentType.signature();
  }

  @Override
  public String name() {
    return myComponentType.name()+"[]";
  }

  @Override
  public String componentSignature() {
    return myComponentType.signature();
  }

  @Override
  public String componentTypeName() {
    return myComponentType.name();
  }

  @Override
  public Type componentType() {
    return myComponentType;
  }

  @Override
  public ClassLoaderReference classLoader() {
    return myComponentType instanceof ReferenceType ? ((ReferenceType)myComponentType).classLoader() : null;
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public boolean isAbstract() {
    return false;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @Override
  public boolean isVerified() {
    return true;
  }

  @Override
  public List<Field> fields() {
    return Collections.emptyList();
  }

  @Override
  public List<Field> visibleFields() {
    return Collections.emptyList();
  }

  @Override
  public List<Field> allFields() {
    return Collections.emptyList();
  }

  @Override
  public Field fieldByName(String s) {
    return null;
  }

  @Override
  public List<Method> methods() {
    return Collections.emptyList();
  }

  @Override
  public List<Method> visibleMethods() {
    return Collections.emptyList();
  }

  @Override
  public List<Method> allMethods() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReferenceType> getThisAndAllSupers() {
    return Collections.emptyList();
  }

  @Override
  public List<Method> methodsByName(String name) {
    return Collections.emptyList();
  }

  @Override
  public List<Method> methodsByName(String name, String sig) {
    return Collections.emptyList();
  }

  @Override
  public List<ReferenceType> nestedTypes() {
    return Collections.emptyList();
  }

  @Override
  public Value getValue(Field field) {
    return null;
  }

  @Override
  public Map<Field, Value> getValues(List<? extends Field> list) {
    return Collections.emptyMap();
  }

  @Override
  public List<ObjectReference> instances(long l) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int majorVersion() {
    return 52;
  }

  @Override
  public int minorVersion() {
    return 0;
  }

  @Override
  public int constantPoolCount() {
    return 0;
  }

  @Override
  public byte[] constantPool() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int modifiers() {
    return 0;
  }

  @Override
  public boolean isPrivate() {
    return false;
  }

  @Override
  public boolean isPackagePrivate() {
    return false;
  }

  @Override
  public boolean isProtected() {
    return false;
  }

  @Override
  public boolean isPublic() {
    return true;
  }

  @Override
  public int compareTo(@NotNull ReferenceType o) {
    return name().compareTo(o.name());
  }
}
