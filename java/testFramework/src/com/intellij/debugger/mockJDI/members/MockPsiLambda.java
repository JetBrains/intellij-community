package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockLocalVariable;
import com.intellij.debugger.mockJDI.MockMirror;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class MockPsiLambda extends MockMirror implements Method {
  private final PsiLambdaExpression myPsiLambdaExpression;
  private final PsiMethod myDeclaringMethod;

  public MockPsiLambda(MockVirtualMachine vm, PsiLambdaExpression psiLambdaExpression) {
    super(vm);
    myPsiLambdaExpression = psiLambdaExpression;
    myDeclaringMethod = Objects.requireNonNull(PsiTreeUtil.getParentOfType(psiLambdaExpression, PsiMethod.class));
  }

  @Override
  public String returnTypeName() {
    return LambdaUtil.getFunctionalInterfaceReturnType(myPsiLambdaExpression).getCanonicalText();
  }

  @Override
  public Type returnType() {
    return MockType.createType(myVirtualMachine, LambdaUtil.getFunctionalInterfaceReturnType(myPsiLambdaExpression));
  }

  @Override
  public List<String> argumentTypeNames() {
    // Captured values are not yet supported in mock
    return ContainerUtil.map(myPsiLambdaExpression.getParameterList().getParameters(), parameter -> parameter.getType().getCanonicalText());
  }

  @Override
  public List<Type> argumentTypes() {
    return ContainerUtil.map(myPsiLambdaExpression.getParameterList().getParameters(), parameter -> MockType.createType(myVirtualMachine, parameter.getType()));
  }

  @Override
  public boolean isAbstract() {
    return false;
  }

  @Override
  public boolean isSynchronized() {
    return false;
  }

  @Override
  public boolean isNative() {
    return false;
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Override
  public boolean isBridge() {
    return false;
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
  public List<Location> allLineLocations() throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public List<Location> allLineLocations(String s, String s1) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public List<Location> locationsOfLine(int i) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public List<Location> locationsOfLine(String s, String s1, int i) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public Location locationOfCodeIndex(long l) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<LocalVariable> variables() throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public List<LocalVariable> variablesByName(String s) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public List<LocalVariable> arguments() {
    return ContainerUtil.map(myPsiLambdaExpression.getParameterList().getParameters(), p -> new MockLocalVariable(myVirtualMachine, p));
  }

  @Override
  public byte[] bytecodes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Location location() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String name() {
    return "lambda$"+myDeclaringMethod.getName()+"$mock";
  }

  @Override
  public String signature() {
    return null;
  }

  @Override
  public String genericSignature() {
    return null;
  }

  @Override
  public ReferenceType declaringType() {
    return myVirtualMachine.createReferenceType(myDeclaringMethod.getContainingClass());
  }

  @Override
  public boolean isStatic() {
    return myDeclaringMethod.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }

  @Override
  public int modifiers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPrivate() {
    return true;
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
    return false;
  }

  @Override
  public MockVirtualMachine virtualMachine() {
    return myVirtualMachine;
  }

  @Override
  public int compareTo(@NotNull Method o) {
    return name().compareTo(o.name());
  }
}
