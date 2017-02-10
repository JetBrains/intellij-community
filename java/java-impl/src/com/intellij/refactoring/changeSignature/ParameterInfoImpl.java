/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ParameterInfoImpl implements JavaParameterInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ParameterInfoImpl");

  public int oldParameterIndex;
  private boolean useAnySingleVariable;
  private String name = "";

  private CanonicalTypes.Type myType;
  String defaultValue = "";

  public ParameterInfoImpl(int oldParameterIndex) {
    this.oldParameterIndex = oldParameterIndex;
  }

  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType) {
    setName(name);
    this.oldParameterIndex = oldParameterIndex;
    setType(aType);
  }

  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue) {
    this(oldParameterIndex, name, aType, defaultValue, false);
  }

  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue, boolean useAnyVariable) {
    this(oldParameterIndex, name, aType);
    this.defaultValue = defaultValue;
    useAnySingleVariable = useAnyVariable;
  }

  public ParameterInfoImpl(int oldParameterIndex, String name, CanonicalTypes.Type typeWrapper, String defaultValue) {
    setName(name);
    this.oldParameterIndex = oldParameterIndex;
    myType = typeWrapper;
    this.defaultValue = defaultValue;
  }

  public int getOldIndex() {
    return oldParameterIndex;
  }

  public void setUseAnySingleVariable(boolean useAnySingleVariable) {
    this.useAnySingleVariable = useAnySingleVariable;
  }

  public void updateFromMethod(PsiMethod method) {
    if (getTypeWrapper() != null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(oldParameterIndex >= 0 && oldParameterIndex < parameters.length);
    final PsiParameter parameter = parameters[oldParameterIndex];
    setName(parameter.getName());
    setType(parameter.getType());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ParameterInfoImpl)) return false;

    ParameterInfoImpl parameterInfo = (ParameterInfoImpl) o;

    if (oldParameterIndex != parameterInfo.oldParameterIndex) return false;
    if (defaultValue != null ? !defaultValue.equals(parameterInfo.defaultValue) : parameterInfo.defaultValue != null) return false;
    if (!getName().equals(parameterInfo.getName())) return false;
    if (!getTypeText().equals(parameterInfo.getTypeText())) return false;

    return true;
  }

  public int hashCode() {
    final String name = getName();
    int result = name != null ? name.hashCode() : 0;
    result = 29 * result + getTypeText().hashCode();
    return result;
  }

  public String getTypeText() {
    if (getTypeWrapper() != null) {
      return getTypeWrapper().getTypeText();
    }
    else {
      return "";
    }
  }

  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (getTypeWrapper() != null) {
      return getTypeWrapper().getType(context, manager);
    } else {
      return null;
    }
  }

  @Override
  public void setType(PsiType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  public String getName() {
    return name;
  }

  public CanonicalTypes.Type getTypeWrapper() {
    return myType;
  }

  public void setName(String name) {
    this.name = name != null ? name : "";
  }

  public boolean isVarargType() {
    return getTypeText().endsWith("...");
  }

  public static ParameterInfoImpl[] fromMethod(PsiMethod method) {
    List<ParameterInfoImpl> result = new ArrayList<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }

  @Nullable
  public PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException {
    if (StringUtil.isEmpty(defaultValue)) return null;
    final PsiExpression expression =
      JavaPsiFacade.getInstance(expr.getProject()).getElementFactory().createExpressionFromText(defaultValue, expr);
    return (PsiExpression)JavaCodeStyleManager.getInstance(expr.getProject()).shortenClassReferences(expression);
  }

  public boolean isUseAnySingleVariable() {
    return useAnySingleVariable;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(final String defaultValue) {
    this.defaultValue = defaultValue;
  }
}
