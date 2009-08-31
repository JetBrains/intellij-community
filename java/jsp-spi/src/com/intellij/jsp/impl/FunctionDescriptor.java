/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.jsp.impl;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public interface FunctionDescriptor extends PsiWritableMetaData, PsiMetaData {
  String getFunctionClass();

  void setFunctionClass(String functionClass);

  String getFunctionSignature();

  void setFunctionSignature(String functionSignature);

  XmlTag getTag();

  int getParameterCount();

  String getFunctionName();

  List<String> getFunctionParameters();

  String getFunctionReturnType();

  @Nullable
  PsiType getResultType();

  @Nullable PsiMethod getReferencedMethod();
}
