// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.ModuleContext;
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModulesScope extends GlobalSearchScope implements CodeInsightContextAwareSearchScope, ActualCodeInsightContextInfo {
  private final ProjectFileIndex myProjectFileIndex;
  private final Set<? extends Module> myModules;

  public ModulesScope(@NotNull Set<? extends Module> modules, @NotNull Project project) {
    super(project);
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myModules = modules;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (CodeInsightContexts.isSharedSourceSupportEnabled(Objects.requireNonNull(getProject()))) {
      Set<Module> modulesOfFile = myProjectFileIndex.getModulesForFile(file, true);
      return ContainerUtil.intersects(myModules, modulesOfFile);
    }
    else {
      Module moduleOfFile = myProjectFileIndex.getModuleForFile(file);
      return myModules.contains(moduleOfFile);
    }
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    return this;
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file) {
    Set<Module> modulesOfFile = myProjectFileIndex.getModulesForFile(file, true);
    Collection<Module> intersection = ContainerUtil.intersection(myModules, modulesOfFile);
    if (!intersection.isEmpty()) {
      ProjectModelContextBridge bridge = ProjectModelContextBridge.getInstance(Objects.requireNonNull(getProject()));
      List<ModuleContext> contexts = ContainerUtil.mapNotNull(intersection, m -> bridge.getContext(m));
      return CodeInsightContextAwareSearchScopes.createContainingContextFileInfo(contexts);
    }
    else {
      return CodeInsightContextAwareSearchScopes.DoesNotContainFileInfo();
    }
  }

  @ApiStatus.Experimental
  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    if (!CodeInsightContexts.isSharedSourceSupportEnabled(Objects.requireNonNull(getProject()))) {
      return contains(file);
    }

    if (!(context instanceof ModuleContext moduleContext)) return false;
    Module contextModule = moduleContext.getModule();
    Set<Module> modulesOfFile = myProjectFileIndex.getModulesForFile(file, true);
    return contextModule != null && myModules.contains(contextModule) && modulesOfFile.contains(contextModule);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myModules.contains(aModule);
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public String toString() {
    return "Modules:" + Arrays.toString(myModules.toArray(Module.EMPTY_ARRAY));
  }
}