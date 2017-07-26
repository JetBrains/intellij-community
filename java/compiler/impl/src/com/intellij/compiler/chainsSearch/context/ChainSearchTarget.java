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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.backwardRefs.SignatureData;

import java.util.Set;

public class ChainSearchTarget {
  private static final Set<String> EXCLUDED_PACKAGES = ContainerUtil.set("java.lang", "java.util.function");

  private final String myClassQName;
  private final byte[] myAcceptedArrayKinds;
  private final PsiType myPsiType;
  private final boolean myIteratorAccess;

  public ChainSearchTarget(String classQName,
                           byte[] arrayKinds,
                           PsiType targetType) {
    this(classQName, arrayKinds, targetType, false);
  }

  private ChainSearchTarget(String classQName,
                           byte[] arrayKinds,
                           PsiType targetType,
                           boolean iteratorAccess) {
    myClassQName = classQName;
    myAcceptedArrayKinds = arrayKinds;
    myPsiType = targetType;
    myIteratorAccess = iteratorAccess;
  }

  public String getClassQName() {
    return myClassQName;
  }

  //@SignatureData.IteratorKind
  public byte[] getArrayKind() {
    return myAcceptedArrayKinds;
  }

  public PsiClass getTargetClass() {
    return PsiUtil.resolveClassInType(myPsiType);
  }

  public boolean isIteratorAccess() {
    return myIteratorAccess;
  }

  public ChainSearchTarget toIterators() {
    return myAcceptedArrayKinds.length == 1 && myAcceptedArrayKinds[0] == SignatureData.ZERO_DIM ?
           new ChainSearchTarget(myClassQName, new byte[]{SignatureData.ARRAY_ONE_DIM, SignatureData.ITERATOR_ONE_DIM}, myPsiType, true) :
           this;
  }

  @Nullable
  public static ChainSearchTarget create(PsiType type) {
    if (type instanceof PsiArrayType) {
      return create((PsiArrayType)type);
    }
    else if (type instanceof PsiClassType) {
      return create((PsiClassType)type);
    }
    return null;
  }

  @Nullable
  private static ChainSearchTarget create(PsiArrayType arrayType) {
    // only 1-dim arrays accepted
    PsiType componentType = arrayType.getComponentType();
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
    if (aClass == null) return null;
    String targetQName = aClass.getQualifiedName();
    if (targetQName == null) return null;
    return new ChainSearchTarget(targetQName, new byte[] {SignatureData.ARRAY_ONE_DIM}, arrayType);
  }

  @Nullable
  private static ChainSearchTarget create(PsiClassType classType) {
    PsiClass resolvedClass = PsiUtil.resolveClassInClassTypeOnly(classType);
    if (resolvedClass == null) return null;
    byte iteratorKind = SignatureData.ZERO_DIM;
    String iteratorClass = getIteratorKind(resolvedClass);
    if (iteratorClass != null) {
      resolvedClass = PsiUtil.resolveClassInClassTypeOnly(PsiUtil.substituteTypeParameter(classType, iteratorClass, 0, false));
      if (resolvedClass == null) return null;
      iteratorKind = SignatureData.ITERATOR_ONE_DIM;
    }
    if (resolvedClass.hasTypeParameters() || resolvedClass instanceof PsiTypeParameter || AnnotationUtil.isAnnotated(resolvedClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, false, false)) return null;
    String packageName = JavaHierarchyUtil.getPackageName(resolvedClass);
    if (packageName == null || EXCLUDED_PACKAGES.contains(packageName)) return null;

    String classQName = resolvedClass.getQualifiedName();
    if (classQName == null) {
      return null;
    }
    return new ChainSearchTarget(classQName, new byte[] {iteratorKind}, classType);
  }

  public static String getIteratorKind(PsiClass resolvedClass) {
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
