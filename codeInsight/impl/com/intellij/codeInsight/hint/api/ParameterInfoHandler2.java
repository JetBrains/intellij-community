package com.intellij.codeInsight.hint.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public interface ParameterInfoHandler2<O extends PsiElement,P,M extends PsiElement> extends ParameterInfoHandler<O,P> {

  @NotNull M[] getParameters(@NotNull O o);

  @NotNull IElementType getDelimiterType();

  @NotNull
  IElementType getRBraceType();

  @NotNull
  Set<Class> getArgumentListAllowedParentClasses();

  @NotNull Class<O> getArgumentListClass();
}