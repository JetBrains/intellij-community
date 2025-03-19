// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ParameterInfoImpl implements JavaParameterInfo {
  private static final Logger LOG = Logger.getInstance(ParameterInfoImpl.class);

  public int oldParameterIndex;
  private boolean useAnySingleVariable;
  private String name = "";

  private CanonicalTypes.Type myType;
  String defaultValue = "";

  /**
   * @see #create(int)
   * @see #createNew()
   */
  public ParameterInfoImpl(int oldParameterIndex) {
    this.oldParameterIndex = oldParameterIndex;
  }

  /**
   * @see #create(int)
   * @see #createNew()
   * @see #withName(String)
   * @see #withType(PsiType)
   */
  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType) {
    setName(name);
    this.oldParameterIndex = oldParameterIndex;
    setType(aType);
  }

  /**
   * @see #create(int)
   * @see #createNew()
   * @see #withName(String)
   * @see #withType(PsiType)
   * @see #withDefaultValue(String)
   */
  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue) {
    this(oldParameterIndex, name, aType, defaultValue, false);
  }

  /**
   * @see #create(int)
   * @see #createNew()
   * @see #withName(String)
   * @see #withType(PsiType)
   * @see #withDefaultValue(String)
   * @see #useAnySingleVariable()
   */
  public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue, boolean useAnyVariable) {
    this(oldParameterIndex, name, aType);
    this.defaultValue = defaultValue;
    useAnySingleVariable = useAnyVariable;
  }

  /**
   * @see #create(int)
   * @see #createNew()
   * @see #withName(String)
   * @see #withType(CanonicalTypes.Type)
   * @see #withDefaultValue(String)
   */
  public ParameterInfoImpl(int oldParameterIndex, String name, CanonicalTypes.Type typeWrapper, String defaultValue) {
    setName(name);
    this.oldParameterIndex = oldParameterIndex;
    myType = typeWrapper;
    this.defaultValue = defaultValue;
  }

  @Override
  public int getOldIndex() {
    return oldParameterIndex;
  }

  @Override
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof ParameterInfoImpl parameterInfo &&
           oldParameterIndex == parameterInfo.oldParameterIndex &&
           Objects.equals(defaultValue, parameterInfo.defaultValue) &&
           getName().equals(parameterInfo.getName()) &&
           getTypeText().equals(parameterInfo.getTypeText());
  }

  @Override
  public int hashCode() {
    final String name = getName();
    int result = name != null ? name.hashCode() : 0;
    result = 29 * result + getTypeText().hashCode();
    return result;
  }

  @Override
  public String getTypeText() {
    return getTypeWrapper() == null ? "" : getTypeWrapper().getTypeText();
  }

  @Override
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    return getTypeWrapper() == null ? null : getTypeWrapper().getType(context, manager);
  }

  @Override
  public void setType(PsiType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public CanonicalTypes.Type getTypeWrapper() {
    return myType;
  }

  @Override
  public void setName(String name) {
    this.name = name != null ? name : "";
  }

  @Override
  public boolean isVarargType() {
    return getTypeText().endsWith("...");
  }

  @Override
  public @Nullable PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException {
    if (StringUtil.isEmpty(defaultValue)) return null;
    try {
      final PsiExpression expression =
        JavaPsiFacade.getElementFactory(expr.getProject()).createExpressionFromText(defaultValue, expr);
      return (PsiExpression)JavaCodeStyleManager.getInstance(expr.getProject()).shortenClassReferences(expression);
    }
    catch (IncorrectOperationException e) {
      //e.g when default value is a kotlin expression
      return null;
    }
  }

  @Override
  public boolean isUseAnySingleVariable() {
    return useAnySingleVariable;
  }

  @Override
  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(final String defaultValue) {
    this.defaultValue = defaultValue;
  }

  /**
   * Returns an array of {@code ParameterInfoImpl} entries which correspond to given method signature.
   *
   * @param method method to create an array from
   * @return an array of ParameterInfoImpl entries
   */
  public static ParameterInfoImpl @NotNull [] fromMethod(@NotNull PsiMethod method) {
    List<ParameterInfoImpl> result = new ArrayList<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      result.add(create(i).withName(parameter.getName()).withType(parameter.getType()));
    }
    return result.toArray(new ParameterInfoImpl[0]);
  }

  /**
   * Returns an array of {@code ParameterInfoImpl} entries which correspond to given method signature with given parameter removed.
   *
   * @param method method to create an array from
   * @param parameterToRemove parameter to remove from method signature
   * @return an array of ParameterInfoImpl entries
   */
  public static ParameterInfoImpl @NotNull [] fromMethodExceptParameter(@NotNull PsiMethod method, @NotNull PsiParameter parameterToRemove) {
    List<ParameterInfoImpl> result = new ArrayList<>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (!parameterToRemove.equals(parameter)) {
        result.add(create(i).withName(parameter.getName()).withType(parameter.getType()));
      }
    }
    return result.toArray(new ParameterInfoImpl[0]);
  }

  @Contract(value = "-> new", pure = true)
  public static @NotNull ParameterInfoImpl createNew() {
    return create(NEW_PARAMETER);
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull ParameterInfoImpl create(int oldParameterIndex) {
    return new ParameterInfoImpl(oldParameterIndex);
  }

  @Contract(value = "_ -> this")
  public @NotNull ParameterInfoImpl withName(@NonNls String name) {
    setName(name);
    return this;
  }

  @Contract(value = "_ -> this")
  public @NotNull ParameterInfoImpl withType(PsiType aType) {
    setType(aType);
    return this;
  }

  @Contract(value = "_ -> this")
  public @NotNull ParameterInfoImpl withType(CanonicalTypes.Type typeWrapper) {
    myType = typeWrapper;
    return this;
  }

  @Contract(value = "_ -> this")
  public @NotNull ParameterInfoImpl withDefaultValue(@NonNls String defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  @Contract(value = "-> this")
  public @NotNull ParameterInfoImpl useAnySingleVariable() {
    useAnySingleVariable = true;
    return this;
  }
}
