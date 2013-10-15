/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;

public abstract class StubIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubIndex");

  private static class StubIndexHolder {
    private static final StubIndex ourInstance = ApplicationManager.getApplication().getComponent(StubIndex.class);
  }
  public static StubIndex getInstance() {
    return StubIndexHolder.ourInstance;
  }

  public abstract <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                    @NotNull Key key,
                                                                    @NotNull Project project,
                                                                    final GlobalSearchScope scope);

  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                    @NotNull Key key,
                                                                    @NotNull Project project,
                                                                    final GlobalSearchScope scope,
                                                                    IdFilter filter) {
    return get(indexKey, key, project, scope);
  }

  public abstract <Key, Psi extends PsiElement> boolean process(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                @NotNull Key key,
                                                                @NotNull Project project,
                                                                GlobalSearchScope scope,
                                                                @NotNull Processor<? super Psi> processor);

  public <Key, Psi extends PsiElement> boolean process(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                @NotNull Key key,
                                                                @NotNull Project project,
                                                                GlobalSearchScope scope,
                                                                IdFilter idFilter,
                                                                @NotNull Processor<? super Psi> processor) {
    return process(indexKey, key, project, scope, processor);
  }

  @NotNull
  public abstract <Key> Collection<Key> getAllKeys(@NotNull StubIndexKey<Key, ?> indexKey, @NotNull Project project);

  public abstract <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project, Processor<K> processor);

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, Processor<K> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexKey, scope.getProject(), processor);
  }

  public <Key, Psi extends PsiElement> Collection<Psi> safeGet(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull final Project project,
                                                               final GlobalSearchScope scope,
                                                               @NotNull Class<Psi> requiredClass) {
    Collection<Psi> collection = getInstance().get(indexKey, key, project, scope);
    for (Iterator<Psi> iterator = collection.iterator(); iterator.hasNext(); ) {
      Psi psi = iterator.next();
      if (!requiredClass.isInstance(psi)) {
        iterator.remove();

        VirtualFile faultyContainer = PsiUtilCore.getVirtualFile(psi);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }

        reportStubPsiMismatch(psi, faultyContainer);
      }
    }

    return collection;
  }

  protected <Psi extends PsiElement> void reportStubPsiMismatch(Psi psi, VirtualFile file) {
    LOG.error("Invalid stub element type in index: " + file + ". found: " + psi);
  }

}
