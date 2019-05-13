/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
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
      ParameterInfoImpl info = new ParameterInfoImpl(i, parameter.getName(), parameter.getType());
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
    return typeElement != null ? typeElement.getText() : null;
  }

  @Override
  public boolean canChangeVisibility() {
    PsiClass containingClass = myMethod.getContainingClass();
    return containingClass != null && !containingClass.isInterface();
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
}
