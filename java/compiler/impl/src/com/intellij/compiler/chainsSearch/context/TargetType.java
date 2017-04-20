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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.SignatureData;

/**
 * @author Dmitry Batkovich
 */
public class TargetType {
  private final String myClassQName;
  private final byte myArrayKind;
  private final PsiType myPsiType;

  public TargetType(String classQName,
                    byte arrayKind,
                    PsiType targetType) {
    myClassQName = classQName;
    myArrayKind = arrayKind;
    myPsiType = targetType;
  }

  public String getClassQName() {
    return myClassQName;
  }

  @SignatureData.IteratorKind
  public byte getArrayKind() {
    return myArrayKind;
  }

  public PsiClass getTargetClass() {
    return PsiUtil.resolveClassInType(myPsiType);
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
    // only 1-dim arrays accepted
    PsiType componentType = arrayType.getComponentType();
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
    if (aClass == null) return null;
    String targetQName = aClass.getQualifiedName();
    if (targetQName == null) return null;
    return new TargetType(targetQName, SignatureData.ARRAY_ONE_DIM, arrayType);
  }

  @Nullable
  private static TargetType create(PsiClassType classType) {
    PsiClass resolvedClass = PsiUtil.resolveClassInClassTypeOnly(classType);
    byte iteratorKind = SignatureData.ZERO_DIM;
    if (resolvedClass == null) return null;
    String iteratorClass = isIterator(resolvedClass);
    if (iteratorClass != null) {
      PsiClassType streamType = (PsiClassType)PsiUtil.substituteTypeParameter(classType, iteratorClass, 0, false);
      if (streamType == null) return null;
      PsiType[] parameters = streamType.getParameters();
      if (parameters.length != 1 || !(parameters[0] instanceof PsiClassType)) return null;
      resolvedClass = PsiUtil.resolveClassInClassTypeOnly(parameters[0]);
      if (resolvedClass == null) return null;
      iteratorKind = SignatureData.ITERATOR_ONE_DIM;
    }
    String classQName = resolvedClass.getQualifiedName();
    if (classQName == null) {
      return null;
    }
    if (resolvedClass.hasTypeParameters()) {
      return null;
    }
    return new TargetType(classQName, iteratorKind, classType);
  }

  private static String isIterator(PsiClass resolvedClass) {
    if (InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
      return CommonClassNames.JAVA_LANG_ITERABLE;
    }
    if (InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_UTIL_ITERATOR)) {
      return CommonClassNames.JAVA_UTIL_ITERATOR;
    }
    if (InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
      return CommonClassNames.JAVA_UTIL_STREAM_STREAM;
    }
    return null;
  }
}
