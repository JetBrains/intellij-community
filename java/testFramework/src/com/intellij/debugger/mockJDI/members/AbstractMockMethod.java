/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.Type;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractMockMethod extends MockTypeComponent implements Method {
  public AbstractMockMethod(Member member, final MockVirtualMachine virtualMachine) {
    super(member, virtualMachine);
  }

  @Override
  public String signature() {
    StringBuilder builder = new StringBuilder();
    builder.append('(');
    for (Type type : argumentTypes()) {
      builder.append(type.signature());
    }
    builder.append(')');
    builder.append(returnType().signature());
    return builder.toString();
  }

  @Override
  public String returnTypeName() {
    return returnType().name();
  }

  @Override
  public Type returnType() {
    return MockType.createType(myVirtualMachine, getReturnType());
  }

  protected abstract Class<?> getReturnType();

  @Override
  public List<String> argumentTypeNames() {
    return argumentTypes().stream().map(Type::name).collect(Collectors.toList());
  }

  @Override
  public List<Type> argumentTypes() {
    ArrayList<Type> types = new ArrayList<>();
    for (Class<?> aClass : getParameterTypes()) {
      types.add(MockType.createType(myVirtualMachine, aClass));
    }
    return types;
  }

  protected abstract Class<?>[] getParameterTypes();

  @Override
  public boolean isAbstract() {
    return Modifier.isAbstract(myMember.getModifiers());
  }

  @Override
  public boolean isSynchronized() {
    return Modifier.isSynchronized(myMember.getModifiers());
  }

  @Override
  public boolean isNative() {
    return Modifier.isNative(myMember.getModifiers());
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @Override
  public boolean isStaticInitializer() {
    return false;
  }

  @Override
  public boolean isObsolete() {
    return false;
  }

  @Override
  public List<Location> allLineLocations() {
    return new ArrayList<>();
  }

  @Override
  public List<Location> allLineLocations(String string,String string1) {
    return new ArrayList<>();
  }

  @Override
  public List<Location> locationsOfLine(int i) {
    return new ArrayList<>();
  }

  @Override
  public List<Location> locationsOfLine(String string,String string1,int i) {
    return new ArrayList<>();
  }

  @Override
  public Location locationOfCodeIndex(long l) {
    throw new UnsupportedOperationException("Not implemented: \"locationOfCodeIndex\" in " + getClass().getName());
  }

  @Override
  public List<LocalVariable> variables() {
    throw new UnsupportedOperationException("Not implemented: \"variables\" in " + getClass().getName());
  }

  @Override
  public List<LocalVariable> variablesByName(String string) {
    throw new UnsupportedOperationException("Not implemented: \"variablesByName\" in " + getClass().getName());
  }

  @Override
  public List<LocalVariable> arguments() {
    throw new UnsupportedOperationException("Not implemented: \"arguments\" in " + getClass().getName());
  }

  @Override
  public byte[] bytecodes() {
    throw new UnsupportedOperationException("Not implemented: \"bytecodes\" in " + getClass().getName());
  }

  @Override
  public Location location() {
    throw new UnsupportedOperationException("Not implemented: \"location\" in " + getClass().getName());
  }

  @Override
  public int compareTo(Method o) {
    throw new UnsupportedOperationException("Not implemented: \"compareTo\" in " + getClass().getName());
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Override
  public boolean isBridge() {
    return false;
  }
}
