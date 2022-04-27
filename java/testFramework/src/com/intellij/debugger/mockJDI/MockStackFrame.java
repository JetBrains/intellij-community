/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI;

import com.intellij.debugger.mockJDI.members.MockPsiLambda;
import com.intellij.debugger.mockJDI.members.MockPsiMethod;
import com.intellij.debugger.mockJDI.types.MockType;
import com.intellij.debugger.mockJDI.values.MockObjectReference;
import com.intellij.debugger.mockJDI.values.MockValue;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.*;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockStackFrame extends MockMirror implements StackFrame {
  private final PsiElement myContext;
  private final List<MockLocalVariable> myLocalVariables = new ArrayList<>();
  private MockObjectReference myThisValue = null;

  public MockStackFrame(final MockVirtualMachine virtualMachine) {
    this(virtualMachine, null);
  }

  public MockStackFrame(final MockVirtualMachine virtualMachine, PsiElement context) {
    super(virtualMachine);
    myContext = context;
  }

  public void addVariable(String name, MockValue value) {
    myLocalVariables.add(new MockLocalVariable(myVirtualMachine, name, (MockType) value.type(), value));
  }

  public void addVariable(MockLocalVariable var) {
    myLocalVariables.add(var);
  }
  
  public void setThisValue(MockObjectReference val) {
    myThisValue = val;
  }

  @Override
  public Location location() {
    if (myContext == null) {
      throw new IllegalStateException("Context is not specified");
    }
    return ReadAction.compute(() -> {
      int offset = myContext.getTextRange().getStartOffset();
      PsiFile file = myContext.getContainingFile();
      int lineNumber = StringUtil.offsetToLineNumber(file.getText(), offset);
      String name = file.getName();
      PsiParameterListOwner psiMethod = PsiTreeUtil.getParentOfType(myContext, PsiMethod.class, PsiLambdaExpression.class);
      if (psiMethod == null) {
        UMethod uMethod = UastContextKt.getUastParentOfType(myContext, UMethod.class);
        if (uMethod != null) {
          psiMethod = uMethod.getJavaPsi();
        }
        else {
          throw new IllegalStateException("Method/lambda not found");
        }
      }
      Method method = psiMethod instanceof PsiMethod ?
                      new MockPsiMethod(myVirtualMachine, (PsiMethod)psiMethod) :
                      new MockPsiLambda(myVirtualMachine, (PsiLambdaExpression)psiMethod);
      return new MockLocation(lineNumber, 0, method, name, name);
    });
  }

  @Override
  public ThreadReference thread() {
    throw new UnsupportedOperationException("Not implemented: \"thread\" in " + getClass().getName());
  }

  @Override
  public ObjectReference thisObject() {
    return myThisValue;
  }

  @Override
  public List<LocalVariable> visibleVariables() {
    return new ArrayList<>(myLocalVariables);
  }

  @Override
  public LocalVariable visibleVariableByName(String string) {
    for (MockLocalVariable localVariable : myLocalVariables) {
      if (localVariable.name().equals(string)) {
        return localVariable;
      }
    }
    return null;
  }

  @Override
  public Value getValue(LocalVariable localVariable) {
    return ((MockLocalVariable) localVariable).getValue();
  }

  @Override
  public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> list) {
    HashMap<LocalVariable, Value> map = new HashMap<>();
    for (LocalVariable variable : list) {
      map.put(variable, getValue(variable));
    }
    return map;
  }

  @Override
  public void setValue(LocalVariable localVariable,Value value) {
    throw new UnsupportedOperationException("Not implemented: \"setValue\" in " + getClass().getName());
  }

  @Override
  public List<Value> getArgumentValues() {
    throw new UnsupportedOperationException("Not implemented: \"getArgumentValues\" in " + getClass().getName());
  }
}
