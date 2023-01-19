/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.members.MockPsiMethod;
import com.intellij.debugger.mockJDI.values.MockClassLoaderReference;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockPsiReferenceType extends MockType implements ReferenceType {
  final PsiClass myClass;

  public MockPsiReferenceType(MockVirtualMachine virtualMachine, PsiClass psiClass) {
    super(virtualMachine);
    myClass = psiClass;
  }

  @Override
  public String name() {
    return ClassUtil.getJVMClassName(myClass);
  }

  @Override
  public String signature() {
    return "L" + name().replace('.', '/') + ";";
  }

  @Override
  public String genericSignature() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoaderReference classLoader() {
    return new MockClassLoaderReference(myVirtualMachine, Object.class.getClassLoader());
  }

  @Override
  public String sourceName() {
    return myClass.getContainingFile().getName();
  }

  @Override
  public List<String> sourceNames(String s) {
    return Collections.singletonList(sourceName());
  }

  @Override
  public List<String> sourcePaths(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String sourceDebugExtension() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isStatic() {
    return myClass.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isAbstract() {
    return myClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  @Override
  public boolean isFinal() {
    return myClass.hasModifierProperty(PsiModifier.FINAL);
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
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Field> visibleFields() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Field> allFields() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Field fieldByName(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Method> methods() {
    return ContainerUtil.map(myClass.getMethods(), m -> new MockPsiMethod(myVirtualMachine, m));
  }

  @Override
  public List<Method> visibleMethods() {
    return StreamEx.of(myClass.getMethods())
      .filter(m -> m.hasModifierProperty(PsiModifier.PUBLIC))
      .<Method>map(m -> new MockPsiMethod(myVirtualMachine, m))
      .toList();
  }

  @Override
  public List<Method> allMethods() {
    return ContainerUtil.map(myClass.getAllMethods(), m -> new MockPsiMethod(myVirtualMachine, m));
  }

  @Override
  public List<Method> methodsByName(String name) {
    return ContainerUtil.map(myClass.findMethodsByName(name, true), m -> new MockPsiMethod(myVirtualMachine, m));
  }

  @Override
  public List<Method> methodsByName(String name, String sig) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ReferenceType> nestedTypes() {
    return Collections.emptyList();
  }

  @Override
  public Value getValue(Field field) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Field, Value> getValues(List<? extends Field> list) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassObjectReference classObject() {
    throw new UnsupportedOperationException();
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
  public List<String> availableStrata() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String defaultStratum() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ObjectReference> instances(long l) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int majorVersion() {
    return PsiUtil.getLanguageLevel(myClass).toJavaVersion().feature + 44;
  }

  @Override
  public int minorVersion() {
    return 0;
  }

  @Override
  public int constantPoolCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] constantPool() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int modifiers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPrivate() {
    return myClass.hasModifierProperty(PsiModifier.PRIVATE);
  }

  @Override
  public boolean isPackagePrivate() {
    return myClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL);
  }

  @Override
  public boolean isProtected() {
    return myClass.hasModifierProperty(PsiModifier.PROTECTED);
  }

  @Override
  public boolean isPublic() {
    return myClass.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public int compareTo(@NotNull ReferenceType o) {
    return name().compareTo(o.name());
  }
}
