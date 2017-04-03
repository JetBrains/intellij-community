/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch.context;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class TargetType {

  private final String myClassQName;
  private final boolean myArray;
  private final PsiType myPsiType;

  public TargetType(String classQName,
                    boolean isArray,
                    PsiType targetType) {
    myClassQName = classQName;
    myArray = isArray;
    myPsiType = targetType;
  }

  public String getClassQName() {
    return myClassQName;
  }

  public boolean isArray() {
    return myArray;
  }

  public PsiType getPsiType() {
    return myPsiType;
  }

  public PsiClass getTargetClass() {
    return PsiUtil.resolveClassInType(myPsiType);
  }

  public boolean isAssignableFrom(PsiClass psiClass) {
    return myPsiType.isAssignableFrom(JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass));
  }

  @Nullable
  public static TargetType create(PsiType type) {
    if (type instanceof PsiArrayType) {
      return create((PsiArrayType)type);
    }
    else if (type instanceof PsiClassType) {
      return create((PsiClassType)type);
    }
    return null;
  }

  @Nullable
  private static TargetType create(PsiArrayType arrayType) {
    PsiType currentComponentType = arrayType.getComponentType();
    while (currentComponentType instanceof PsiArrayType) {
      currentComponentType = ((PsiArrayType)currentComponentType).getComponentType();
    }
    if (!(currentComponentType instanceof PsiClassType)) {
      return null;
    }
    String targetQName = arrayType.getCanonicalText();
    return new TargetType(targetQName, true, arrayType);
  }

  @Nullable
  private static TargetType create(PsiClassType classType) {
    PsiClassType.ClassResolveResult resolvedGenerics = classType.resolveGenerics();
    PsiClass resolvedClass = resolvedGenerics.getElement();
    if (resolvedClass == null) {
      return null;
    }
    String classQName = resolvedClass.getQualifiedName();
    if (classQName == null) {
      return null;
    }
    if (resolvedClass.hasTypeParameters()) {
      return null;
    }
    return new TargetType(classQName, false, classType);
  }
}
