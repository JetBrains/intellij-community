// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.Field;
import com.sun.jdi.Type;

import java.lang.reflect.Modifier;

public class MockField extends MockTypeComponent implements Field {
  public MockField(java.lang.reflect.Field member, final MockVirtualMachine virtualMachine) {
    super(member, virtualMachine);
  }

  public java.lang.reflect.Field getField() {
    return (java.lang.reflect.Field)myMember;
  }

  @Override
  public String typeName() {
    return type().name();
  }

  @Override
  public Type type() {
    return MockType.createType(myVirtualMachine, getField().getType());
  }

  @Override
  public boolean isTransient() {
    return Modifier.isTransient(myMember.getModifiers());
  }

  @Override
  public boolean isVolatile() {
    return Modifier.isVolatile(myMember.getModifiers());
  }

  @Override
  public boolean isEnumConstant() {
    return getField().isEnumConstant();
  }

  @Override
  public int compareTo(Field o) {
    throw new UnsupportedOperationException("Not implemented: \"compareTo\" in " + getClass().getName());
  }
}
