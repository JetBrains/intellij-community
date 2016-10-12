/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

class HighlightingCaches {
  public static HighlightingCaches getInstance(Project project) {
    return ServiceManager.getService(project, HighlightingCaches.class);
  }

  private final List<Map<?,?>> allCaches = ContainerUtil.createConcurrentList();

  public HighlightingCaches(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) {
          allCaches.forEach(Map::clear);
        }
      }

      @Override
      public void afterPsiChanged(boolean isPhysical) {

      }
    });
  }

  // baseClass -> list of direct subclasses
  final ConcurrentMap<PsiClass, PsiClass[]> DIRECT_SUB_CLASSES = createWeakCache();
  // baseClass -> all sub classes transitively, including anonymous
  final ConcurrentMap<PsiClass, Iterable<PsiClass>> ALL_SUB_CLASSES = createWeakCache();
  // baseMethod -> all overriding methods
  final Map<PsiMethod, Iterable<PsiMethod>> OVERRIDING_METHODS = createWeakCache();

  @NotNull
  private <T,V> ConcurrentMap<T,V> createWeakCache() {
    ConcurrentMap<T, V> map = ContainerUtil.createConcurrentWeakKeySoftValueMap(10, 0.7f, Runtime.getRuntime().availableProcessors(), ContainerUtil.canonicalStrategy());
    allCaches.add(map);
    return map;
  }
}
