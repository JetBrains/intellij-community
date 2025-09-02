// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.util.*;
import java.util.function.Supplier;

public abstract class StubIndex {
  private static final Supplier<StubIndex> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    return ApplicationManager.getApplication().getService(StubIndex.class);
  });

  public static StubIndex getInstance() {
    return ourInstance.get();
  }

  @Internal
  public StubIndex() {
  }

  /**
   * @deprecated use {@link #getElements(StubIndexKey, Object, Project, GlobalSearchScope, Class)}
   */
  @Deprecated(forRemoval = true)
  @Unmodifiable <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                  @NotNull Key key,
                                                                  @NotNull Project project,
                                                                  final @Nullable GlobalSearchScope scope) {
    List<Psi> result = new SmartList<>();
    processElements(indexKey, key, project, scope, (Class<Psi>)PsiElement.class, Processors.cancelableCollectProcessor(result));
    return result;
  }

  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull Project project,
                                                               @Nullable GlobalSearchScope scope,
                                                               @NotNull Class<Psi> requiredClass,
                                                               @NotNull Processor<? super Psi> processor) {
    return processElements(indexKey, key, project, scope, null, requiredClass, processor);
  }

  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull Project project,
                                                               @Nullable GlobalSearchScope scope,
                                                               @Nullable IdFilter idFilter,
                                                               @NotNull Class<Psi> requiredClass,
                                                               @NotNull Processor<? super Psi> processor) {
    return processElements(indexKey, key, project, scope, requiredClass, processor);
  }

  public abstract @NotNull @Unmodifiable <Key> Collection<Key> getAllKeys(@NotNull StubIndexKey<Key, ?> indexKey, @NotNull Project project);

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project, @NotNull Processor<? super K> processor) {
    return processAllKeys(indexKey, processor, GlobalSearchScope.allScope(project), null);
  }

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey,
                                    @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope) {
    return processAllKeys(indexKey, processor, scope, null);
  }

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexKey, Objects.requireNonNull(scope.getProject()), processor);
  }

  public static @NotNull @Unmodifiable <Key, Psi extends PsiElement> Collection<Psi> getElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                                                 @NotNull Key key,
                                                                                                 final @NotNull Project project,
                                                                                                 final @Nullable GlobalSearchScope scope,
                                                                                                 @NotNull Class<Psi> requiredClass) {
    return getElements(indexKey, key, project, scope, null, requiredClass);
  }

  public static @NotNull @Unmodifiable <Key, Psi extends PsiElement> Collection<Psi> getElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                                                 @NotNull Key key,
                                                                                                 final @NotNull Project project,
                                                                                                 final @Nullable GlobalSearchScope scope,
                                                                                                 @Nullable IdFilter idFilter,
                                                                                                 @NotNull Class<Psi> requiredClass) {
    final List<Psi> result = new SmartList<>();
    Processor<Psi> processor = Processors.cancelableCollectProcessor(result);
    getInstance().processElements(indexKey, key, project, scope, idFilter, requiredClass, processor);
    return result;
  }

  /**
   * @return lazily reified iterator of VirtualFile's.
   */
  public abstract @NotNull <Key> Iterator<VirtualFile> getContainingFilesIterator(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                         @NotNull @NonNls Key dataKey,
                                                                         @NotNull Project project,
                                                                         @NotNull GlobalSearchScope scope);

  /**
   * @deprecated use {@link StubIndex#getContainingFilesIterator(StubIndexKey, Object, Project, GlobalSearchScope)}
   */
  @Deprecated(forRemoval = true)
  public @NotNull <Key> @Unmodifiable Set<VirtualFile> getContainingFiles(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                          @NotNull @NonNls Key dataKey,
                                                                          @NotNull Project project,
                                                                          @NotNull GlobalSearchScope scope) {
    return ContainerUtil.newHashSet(getContainingFilesIterator(indexKey, dataKey, project, scope));
  }

  @ApiStatus.Experimental
  public abstract <Key> int getMaxContainingFileCount(@NotNull StubIndexKey<Key, ?> indexKey,
                                                      @NotNull @NonNls Key dataKey,
                                                      @NotNull Project project,
                                                      @NotNull GlobalSearchScope scope);



  public abstract void forceRebuild(@NotNull Throwable e);

  /**
   * @param fileElementType {@link IFileElementType} to track changes for.
   * @return {@link ModificationTracker} that changes stamp on every file update (with corresponding {@link IFileElementType})
   * for which the stub has changed.
   * @implNote doesn't track changes of files with binary content. Modification tracking happens before the StubIndex update, so one can use
   * this tracker to react on stub changes without performing the index update. File is considered modified if a stub for its actual content
   * differs from what is stored in the index. Modification detector might react false-positively when the number of changed files is big.
   */
  @Internal
  @ApiStatus.Experimental
  public abstract @NotNull ModificationTracker getPerFileElementTypeModificationTracker(@NotNull IFileElementType fileElementType);

  @Internal
  @ApiStatus.Experimental
  public abstract @NotNull ModificationTracker getStubIndexModificationTracker(@NotNull Project project);
}
