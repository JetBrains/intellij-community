// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * The biggest possible scope: every file on the planet belongs to this.
 */
public class EverythingGlobalScope extends GlobalSearchScope {
  public EverythingGlobalScope(Project project) {
    super(project);
  }

  public EverythingGlobalScope() {
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getNameText();
  }

  public static @NotNull @Nls String getNameText() {
    return CoreBundle.message("scope.name.all.places");
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
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
  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return true;
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    return project != null ? FileIndexFacade.getInstance(project).getUnloadedModuleDescriptions() : Collections.emptySet();
  }

  @NotNull
  @Override
  public GlobalSearchScope union(@NotNull SearchScope scope) {
    return this;
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    return scope2;
  }
}