/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
