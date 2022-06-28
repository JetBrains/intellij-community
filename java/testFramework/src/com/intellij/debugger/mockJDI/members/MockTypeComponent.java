/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.members;

import com.sun.jdi.TypeComponent;
import com.sun.jdi.ReferenceType;
import com.intellij.debugger.mockJDI.MockAccessibleMirror;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

public class MockTypeComponent extends MockAccessibleMirror implements TypeComponent{
  protected Member myMember;

  public MockTypeComponent(Member member, final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
    myMember = member;
  }

  @Override
  public String name() {
    return myMember.getName();
  }

  @Override
  public String signature() {
    throw new UnsupportedOperationException("Not implemented: \"signature\" in " + getClass().getName());
  }

  @Override
  public String genericSignature() {
    throw new UnsupportedOperationException("Not implemented: \"genericSignature\" in " + getClass().getName());
  }

  @Override
  public ReferenceType declaringType() {
    return myVirtualMachine.createReferenceType(myMember.getDeclaringClass());
  }

  @Override
  public boolean isStatic() {
    return Modifier.isStatic(myMember.getModifiers());
  }

  @Override
  public boolean isFinal() {
    return Modifier.isFinal(myMember.getModifiers());
  }

  @Override
  public boolean isSynthetic() {
    return myMember.isSynthetic();
  }
}
