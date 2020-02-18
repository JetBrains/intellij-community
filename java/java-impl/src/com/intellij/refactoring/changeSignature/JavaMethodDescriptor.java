// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.psi.util.AccessModifier;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaMethodDescriptor implements MethodDescriptor<ParameterInfoImpl, String> {

  private final PsiMethod myMethod;

  public JavaMethodDescriptor(PsiMethod method) {
    myMethod = method;
  }

  @Override
  public String getName() {
    return myMethod.getName();
  }

  @Override
  public List<ParameterInfoImpl> getParameters() {
    final ArrayList<ParameterInfoImpl> result = new ArrayList<>();
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      ParameterInfoImpl info = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(parameter.getType());
      info.defaultValue = "";
      result.add(info);
    }
    return result;
  }

  @Override
  public String getVisibility() {
    return VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
  }

  @Override
  public PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public int getParametersCount() {
    return myMethod.getParameterList().getParametersCount();
  }

  @Nullable
  public String getReturnTypeText() {
    final PsiTypeElement typeElement = myMethod.getReturnTypeElement();
    if (typeElement != null) {
      PsiType type = typeElement.getType();
      if (type.getAnnotations().length > 0) {
        return type.getPresentableText(true);
      }
      return typeElement.getText();
    }
    return null;
  }

  @Override
  public boolean canChangeVisibility() {
    return AccessModifier.getAvailableModifiers(myMethod).size() > 1;
  }

  @Override
  public boolean canChangeParameters() {
    return true;
  }

  @Override
  public ReadWriteOption canChangeReturnType() {
    return myMethod.isConstructor() ? ReadWriteOption.None : ReadWriteOption.ReadWrite;
  }

  @Override
  public boolean canChangeName() {
    return !myMethod.isConstructor();
  }

  public List<AccessModifier> getAllowedModifiers() {
    return AccessModifier.getAvailableModifiers(myMethod);
  }
}
