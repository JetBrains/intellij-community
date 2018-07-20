// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class CompositeReflectionAccessor implements ReflectionAccessor {
  private final List<ReflectionAccessor> myAccessors = new ArrayList<>();

  private CompositeReflectionAccessor() {
  }

  private void registerAccessor(@NotNull ReflectionAccessor accessor) {
    myAccessors.add(accessor);
  }

  public static ReflectionAccessor createAccessorToEverything(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    CompositeReflectionAccessor compositeAccessor = new CompositeReflectionAccessor();
    compositeAccessor.registerAccessor(new FieldReflectionAccessor(psiClass, elementFactory));
    compositeAccessor.registerAccessor(new MethodReflectionAccessor(psiClass, elementFactory));
    compositeAccessor.registerAccessor(new ConstructorReflectionAccessor(psiClass, elementFactory));
    compositeAccessor.registerAccessor(new MethodReferenceReflectionAccessor(psiClass, elementFactory));

    return compositeAccessor;
  }

  @Override
  public void accessThroughReflection(@NotNull PsiElement element) {
    for (ReflectionAccessor accessor : myAccessors) {
      accessor.accessThroughReflection(element);
    }
  }
}
