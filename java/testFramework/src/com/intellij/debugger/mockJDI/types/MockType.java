/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockMirror;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.sun.jdi.Type;

public abstract class MockType extends MockMirror implements Type {
  public MockType(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  public static MockType createType(final MockVirtualMachine virtualMachine, Class<?> aClass) {
    if (aClass.isPrimitive()) {
      if (aClass == boolean.class) {
        return virtualMachine.getBooleanType();
      }
      if (aClass == int.class) {
        return virtualMachine.getIntType();
      }
      if (aClass == long.class) {
        return virtualMachine.getLongType();
      }
      if (aClass == short.class) {
        return virtualMachine.getShortType();
      }
      if (aClass == void.class) {
        return virtualMachine.getVoidType();
      }
      throw new UnsupportedOperationException("create type for " + aClass);
    }
    return virtualMachine.createReferenceType(aClass);
  }

  public static MockType createType(final MockVirtualMachine virtualMachine, PsiType type) {
    if (type instanceof PsiArrayType) {
      return new MockArrayType(createType(virtualMachine, ((PsiArrayType)type).getComponentType()));
    }
    if (type instanceof PsiPrimitiveType) {
      if (type.equals(PsiTypes.booleanType())) {
        return virtualMachine.getBooleanType();
      }
      if (type.equals(PsiTypes.intType())) {
        return virtualMachine.getIntType();
      }
      if (type.equals(PsiTypes.longType())) {
        return virtualMachine.getLongType();
      }
      if (type.equals(PsiTypes.shortType())) {
        return virtualMachine.getShortType();
      }
      if (type.equals(PsiTypes.voidType())) {
        return virtualMachine.getVoidType();
      }
    }
    if (type instanceof PsiClassType) {
      PsiClass cls = PsiUtil.resolveClassInClassTypeOnly(type);
      if (cls != null) {
        return virtualMachine.createReferenceType(cls);
      }
    }
    throw new UnsupportedOperationException("create type for " + type);
  }

  @Override
  public String signature() {
    throw new UnsupportedOperationException("Not implemented: \"signature\" in " + getClass().getName());
  }

  @Override
  public String name() {
    throw new UnsupportedOperationException("Not implemented: \"name\" in " + getClass().getName());
  }
}
