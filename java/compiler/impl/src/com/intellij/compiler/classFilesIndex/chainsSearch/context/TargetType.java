/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class TargetType {

  private final String myClassQName;
  private final boolean myArray;
  private final PsiType myPsiType;

  public TargetType(final String classQName,
                    final boolean isArray,
                    final PsiType targetType) {
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

  @Nullable
  public static TargetType create(final PsiArrayType arrayType) {
    PsiType currentComponentType = arrayType.getComponentType();
    while (currentComponentType instanceof PsiArrayType) {
      currentComponentType = ((PsiArrayType)currentComponentType).getComponentType();
    }
    if (!(currentComponentType instanceof PsiClassType)) {
      return null;
    }
    final String targetQName = arrayType.getCanonicalText();
    return new TargetType(targetQName, true, arrayType);
  }

  @Nullable
  public static TargetType create(final PsiClassType classType) {
    final PsiClassType.ClassResolveResult resolvedGenerics = classType.resolveGenerics();
    final PsiClass resolvedClass = resolvedGenerics.getElement();
    if (resolvedClass == null) {
      return null;
    }
    final String classQName = resolvedClass.getQualifiedName();
    if (classQName == null) {
      return null;
    }
    if (resolvedClass.hasTypeParameters()) {
      return null;
    }
    return new TargetType(classQName, false, classType);
  }
}
