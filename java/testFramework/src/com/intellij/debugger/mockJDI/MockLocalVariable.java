/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI;

import com.intellij.debugger.mockJDI.types.MockType;
import com.intellij.debugger.mockJDI.values.MockValue;
import com.intellij.psi.PsiVariable;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class MockLocalVariable extends MockMirror implements LocalVariable {
  private final String myName;
  private final MockType myType;
  private final MockValue myValue;

  public MockLocalVariable(MockVirtualMachine virtualMachine, PsiVariable psiVariable) {
    this(virtualMachine, psiVariable.getName(), MockType.createType(virtualMachine, psiVariable.getType()), null);
  }

  public MockLocalVariable(final MockVirtualMachine virtualMachine, String name, MockType type, MockValue value) {
    super(virtualMachine);
    myName = name;
    myType = type;
    myValue = value;
  }

  @Override
  public String name() {
    return myName;
  }

  @Override
  public String typeName() {
    return myType.name();
  }

  @Override
  public Type type() {
    return myType;
  }

  @Override
  public String signature() {
    return myType.signature();
  }

  @Override
  public String genericSignature() {
    throw new UnsupportedOperationException("Not implemented: \"genericSignature\" in " + getClass().getName());
  }

  @Override
  public boolean isVisible(StackFrame stackFrame) {
    return ((MockStackFrame)stackFrame).visibleVariables().contains(this);
  }

  @Override
  public boolean isArgument() {
    return false;
  }

  @Override
  public int compareTo(LocalVariable o) {
    return name().compareTo(o.name());
  }

  public Value getValue() {
    return myValue;
  }
}
