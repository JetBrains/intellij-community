package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockLocalVariable;
import com.intellij.debugger.mockJDI.MockMirror;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class MockPsiMethod extends MockMirror implements Method {
  private final PsiMethod myPsiMethod;

  public MockPsiMethod(MockVirtualMachine vm, PsiMethod psiMethod) {
    super(vm);
    myPsiMethod = psiMethod;
  }

  @Override
  public String returnTypeName() {
    return myPsiMethod.getReturnType().getCanonicalText();
  }

  @Override
  public Type returnType() {
    return MockType.createType(myVirtualMachine, myPsiMethod.getReturnType());
  }

  @Override
  public List<String> argumentTypeNames() {
    return ContainerUtil.map(myPsiMethod.getParameterList().getParameters(), parameter -> parameter.getType().getCanonicalText());
  }

  @Override
  public List<Type> argumentTypes() {
    return ContainerUtil.map(myPsiMethod.getParameterList().getParameters(), parameter -> MockType.createType(myVirtualMachine, parameter.getType()));
  }

  @Override
  public boolean isAbstract() {
    return myPsiMethod.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  @Override
  public boolean isSynchronized() {
    return myPsiMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED);
  }

  @Override
  public boolean isNative() {
    return myPsiMethod.hasModifierProperty(PsiModifier.NATIVE);
  }

  @Override
  public boolean isVarArgs() {
    return myPsiMethod.isVarArgs();
  }

  @Override
  public boolean isBridge() {
    return false; // PsiMethods are never bridges
  }

  @Override
  public boolean isConstructor() {
    return myPsiMethod.isConstructor();
  }

  @Override
  public boolean isStaticInitializer() {
    return false; // never mock static initializers
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
    return ContainerUtil.map(myPsiMethod.getParameterList().getParameters(), p -> new MockLocalVariable(myVirtualMachine, p));
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
    return myPsiMethod.getName();
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
    return ReadAction.compute(() -> myVirtualMachine.createReferenceType(Objects.requireNonNull(myPsiMethod.getContainingClass())));
  }

  @Override
  public boolean isStatic() {
    return myPsiMethod.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isFinal() {
    return myPsiMethod.hasModifierProperty(PsiModifier.FINAL);
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public int modifiers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPrivate() {
    return myPsiMethod.hasModifierProperty(PsiModifier.PRIVATE);
  }

  @Override
  public boolean isPackagePrivate() {
    return myPsiMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL);
  }

  @Override
  public boolean isProtected() {
    return myPsiMethod.hasModifierProperty(PsiModifier.PROTECTED);
  }

  @Override
  public boolean isPublic() {
    return myPsiMethod.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public int compareTo(@NotNull Method o) {
    return name().compareTo(o.name());
  }
}
