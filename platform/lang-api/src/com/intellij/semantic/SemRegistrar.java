/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;

/**
 * @author peter
 */
public interface SemRegistrar {

  <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key, ElementPattern<? extends V> place, NullableFunction<V, T> provider);

}
