// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Collections.emptyList;

/**
 * @see SemContributor#registerSemProviders(SemRegistrar, Project)
 */
public interface SemRegistrar {
  @SuppressWarnings("unchecked")
  default <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key,
                                                                                       ElementPattern<? extends V> place,
                                                                                       Function<? super V, ? extends @Nullable T> provider) {
    registerSemProvider(key, (element, context) -> {
      if (place.accepts(element, context)) {
        return Collections.singleton(provider.apply((V)element));
      }
      return emptyList();
    });
  }

  @SuppressWarnings("unchecked")
  default <T extends SemElement, V extends PsiElement> void registerRepeatableSemElementProvider(SemKey<T> key,
                                                                                                 ElementPattern<? extends V> place,
                                                                                                 Function<? super V, ? extends @Nullable Collection<T>> provider) {
    registerSemProvider(key, (element, context) -> {
      if (place.accepts(element, context)) {
        return provider.apply((V)element);
      }
      return null;
    });
  }

  <T extends SemElement> void registerSemProvider(
    SemKey<T> key,
    BiFunction<? super PsiElement, ? super ProcessingContext, ? extends Collection<T>> provider
  );
}
