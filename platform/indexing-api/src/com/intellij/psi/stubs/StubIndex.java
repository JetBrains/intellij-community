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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.IdIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class StubIndex {
  private static class StubIndexHolder {
    private static final StubIndex ourInstance = ApplicationManager.getApplication().getComponent(StubIndex.class);
  }
  public static StubIndex getInstance() {
    return StubIndexHolder.ourInstance;
  }

  /**
   * @deprecated use {@link #getElements(StubIndexKey, Object, Project, GlobalSearchScope, Class)}
   */
  @Deprecated
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull Key key,
                                                           @NotNull Project project,
                                                           @Nullable final GlobalSearchScope scope) {
    List<Psi> result = new SmartList<>();
    processElements(indexKey, key, project, scope, (Class<Psi>)PsiElement.class, Processors.cancelableCollectProcessor(result));
    return result;
  }

  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull Project project,
                                                               @Nullable GlobalSearchScope scope,
                                                               Class<Psi> requiredClass,
                                                               @NotNull Processor<? super Psi> processor) {
    return processElements(indexKey, key, project, scope, null, requiredClass, processor);
  }

  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                @NotNull Key key,
                                                                @NotNull Project project,
                                                                @Nullable GlobalSearchScope scope,
                                                                IdFilter idFilter,
                                                                @NotNull Class<Psi> requiredClass,
                                                                @NotNull Processor<? super Psi> processor) {
    return processElements(indexKey, key, project, scope, requiredClass, processor);
  }

  @NotNull
  public abstract <Key> Collection<Key> getAllKeys(@NotNull StubIndexKey<Key, ?> indexKey, @NotNull Project project);

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project, Processor<K> processor) {
    return processAllKeys(indexKey, processor, GlobalSearchScope.allScope(project), null);
  }

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Processor<K> processor,
                                    @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexKey, ObjectUtils.assertNotNull(scope.getProject()), processor);
  }

  /**
   * @deprecated use {@link #getElements(StubIndexKey, Object, Project, GlobalSearchScope, Class)}
   */
  @Deprecated
  public <Key, Psi extends PsiElement> Collection<Psi> safeGet(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull final Project project,
                                                               final GlobalSearchScope scope,
                                                               @NotNull Class<Psi> requiredClass) {
    return getElements(indexKey, key, project, scope, requiredClass);
  }

  public static <Key, Psi extends PsiElement> Collection<Psi> getElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                          @NotNull Key key,
                                                                          @NotNull final Project project,
                                                                          @Nullable final GlobalSearchScope scope,
                                                                          @NotNull Class<Psi> requiredClass) {
    return getElements(indexKey, key, project, scope, null, requiredClass);
  }

  public static <Key, Psi extends PsiElement> Collection<Psi> getElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                          @NotNull Key key,
                                                                          @NotNull final Project project,
                                                                          @Nullable final GlobalSearchScope scope,
                                                                          @Nullable IdFilter idFilter,
                                                                          @NotNull Class<Psi> requiredClass) {
    final List<Psi> result = new SmartList<>();
    Processor<Psi> processor = Processors.cancelableCollectProcessor(result);
    getInstance().processElements(indexKey, key, project, scope, idFilter, requiredClass, processor);
    return result;
  }

  @NotNull
  public abstract <Key> IdIterator getContainingIds(@NotNull StubIndexKey<Key, ?> indexKey, @NotNull Key dataKey,
                                                     @NotNull Project project,
                                                     @NotNull final GlobalSearchScope scope);

  public abstract void forceRebuild(@NotNull Throwable e);
}
