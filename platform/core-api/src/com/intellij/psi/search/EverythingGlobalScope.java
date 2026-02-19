// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

/**
 * The biggest possible scope: every file on the planet belongs to this.
 * <p>
 * If one have passed {@link EverythingGlobalScope} as a filter to index query in {@link com.intellij.util.indexing.FileBasedIndex} or {@link com.intellij.psi.stubs.StubIndex}
 * then all indexable project files will be returned. In case when project is not specified an exception will be thrown.
 * </p>
 */
public class EverythingGlobalScope extends GlobalSearchScope {
  /**
   * @deprecated Use {@link GlobalSearchScope#everythingScope(Project)} instead
   */
  @Deprecated
  public EverythingGlobalScope(Project project) {
    super(project);
  }

  /**
   * Use {@link GlobalSearchScope#everythingScope(Project)} instead to make index query
   */
  public EverythingGlobalScope() {
  }

  @Override
  public @NotNull String getDisplayName() {
    return getNameText();
  }

  public static @NotNull @Nls String getNameText() {
    return CoreBundle.message("scope.name.all.places");
  }

  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public boolean isForceSearchingInLibrarySources() {
    return true;
  }

  @Override
  public boolean isSearchInModuleContent(final @NotNull Module aModule) {
    return true;
  }

  @Override
  public @NotNull @Unmodifiable Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    return project != null ? FileIndexFacade.getInstance(project).getUnloadedModuleDescriptions() : Collections.emptySet();
  }

  @Override
  public @NotNull GlobalSearchScope union(@NotNull SearchScope scope) {
    return this;
  }

  @Override
  public @NotNull SearchScope intersectWith(@NotNull SearchScope scope2) {
    return scope2;
  }
}