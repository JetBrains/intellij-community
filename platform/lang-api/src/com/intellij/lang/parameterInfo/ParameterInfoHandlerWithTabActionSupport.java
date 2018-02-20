// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface ParameterInfoHandlerWithTabActionSupport<ParameterOwner extends PsiElement, ParameterType, ActualParameterType extends PsiElement>
  extends ParameterInfoHandler<ParameterOwner, ParameterType> {

  @NotNull ActualParameterType[] getActualParameters(@NotNull ParameterOwner o);

  @NotNull IElementType getActualParameterDelimiterType();

  @NotNull
  IElementType getActualParametersRBraceType();

  @NotNull
  Set<Class> getArgumentListAllowedParentClasses();

  @SuppressWarnings("TypeParameterExtendsFinalClass") // keep historical signature for compatibility
  @NotNull
  Set<? extends Class> getArgListStopSearchClasses();

  @NotNull Class<ParameterOwner> getArgumentListClass();

  @Override
  default boolean isWhitespaceSensitive() {
    return getActualParameterDelimiterType() == TokenType.WHITE_SPACE;
  }
}
