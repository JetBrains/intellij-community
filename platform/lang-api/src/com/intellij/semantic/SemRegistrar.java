// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;

import java.util.Collection;

/**
 * @see com.intellij.semantic.SemContributor#registerSemProviders(SemRegistrar, Project)
 * @author peter
 */
public interface SemRegistrar {

  <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key, ElementPattern<? extends V> place, NullableFunction<? super V, ? extends T> provider);

  <T extends SemElement, V extends PsiElement> void registerRepeatableSemElementProvider(SemKey<T> key, ElementPattern<? extends V> place, NullableFunction<? super V, ? extends Collection<T>> provider);

}
