// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LightParameterListBuilder extends LightElement implements PsiParameterList {
  private final List<PsiParameter> myParameters = new ArrayList<>();
  private PsiParameter[] myCachedParameters;

  public LightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public void addParameter(PsiParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
  }

  @Override
  public String toString() {
    return "Light parameter list";
  }

  @Override
  public PsiParameter @NotNull [] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = PsiParameter.EMPTY_ARRAY;
      }
      else {
        myCachedParameters = myParameters.toArray(PsiParameter.EMPTY_ARRAY);
      }
    }

    return myCachedParameters;
  }

  @Override
  public @Nullable PsiParameter getParameter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index is negative: " + index);
    }
    if (index < myParameters.size()) return myParameters.get(index);
    return null;
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String getText() {
    return myParameters.stream()
      .map(parameter -> parameter.getText())
      .filter(Objects::nonNull)
      .collect(Collectors.joining(",", "(", ")"));
  }
}
