package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
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

  @NotNull Class<ParameterOwner> getArgumentListClass();
}