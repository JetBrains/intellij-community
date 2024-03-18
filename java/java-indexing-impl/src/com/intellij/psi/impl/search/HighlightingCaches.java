// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@Service(Service.Level.PROJECT)
final class HighlightingCaches {
  public static HighlightingCaches getInstance(Project project) {
    return project.getService(HighlightingCaches.class);
  }

  private final List<Map<?,?>> allCaches = ContainerUtil.createConcurrentList();

  HighlightingCaches(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) {
          allCaches.forEach(Map::clear);
        }
      }
    });
  }

  // baseClass -> list of direct subclasses
  final ConcurrentMap<PsiClass, PsiClass[]> DIRECT_SUB_CLASSES = createWeakCache();
  // baseClass -> all sub classes transitively, including anonymous
  final ConcurrentMap<PsiClass, Iterable<PsiClass>> ALL_SUB_CLASSES = createWeakCache();
  // baseClass -> all sub classes transitively, excluding anonymous
  final ConcurrentMap<PsiClass, Iterable<PsiClass>> ALL_SUB_CLASSES_NO_ANONYMOUS = createWeakCache();
  // baseMethod -> all overriding methods
  final Map<PsiMethod, Iterable<PsiMethod>> OVERRIDING_METHODS = createWeakCache();

  private @NotNull <T,V> ConcurrentMap<T,V> createWeakCache() {
    ConcurrentMap<T, V> map = CollectionFactory.createConcurrentWeakKeySoftValueMap(10, 0.7f, Runtime.getRuntime().availableProcessors(),
                                                                                    HashingStrategy.canonical());
    allCaches.add(map);
    return map;
  }
}
