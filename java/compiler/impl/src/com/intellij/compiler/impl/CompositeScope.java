// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.ExportableUserDataHolderBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class CompositeScope extends ExportableUserDataHolderBase implements CompileScope{
  private final List<CompileScope> myScopes = new ArrayList<>();

  public CompositeScope(@NotNull CompileScope scope1, @NotNull CompileScope scope2) {
    addScope(scope1);
    addScope(scope2);
  }

  public CompositeScope(CompileScope @NotNull [] scopes) {
    for (CompileScope scope : scopes) {
      addScope(scope);
    }
  }

  private void addScope(@NotNull CompileScope scope) {
    if (scope instanceof CompositeScope) {
      final CompositeScope compositeScope = (CompositeScope)scope;
      for (CompileScope childScope : compositeScope.myScopes) {
        addScope(childScope);
      }
    }
    else {
      myScopes.add(scope);
    }
  }

  @Override
  public VirtualFile @NotNull [] getFiles(FileType fileType, boolean inSourceOnly) {
    Set<VirtualFile> allFiles = new HashSet<>();
    for (CompileScope scope : myScopes) {
      final VirtualFile[] files = scope.getFiles(fileType, inSourceOnly);
      if (files.length > 0) {
        ContainerUtil.addAll(allFiles, files);
      }
    }
    return VfsUtilCore.toVirtualFileArray(allFiles);
  }

  @Override
  public boolean belongs(@NotNull String url) {
    for (CompileScope scope : myScopes) {
      if (scope.belongs(url)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Module @NotNull [] getAffectedModules() {
    Set<Module> modules = new HashSet<>();
    for (final CompileScope compileScope : myScopes) {
      ContainerUtil.addAll(modules, compileScope.getAffectedModules());
    }
    return modules.toArray(Module.EMPTY_ARRAY);
  }

  @Override
  public Collection<ModuleSourceSet> getAffectedSourceSets() {
    Set<ModuleSourceSet> sets = new SmartHashSet<>();
    for (CompileScope scope : myScopes) {
      sets.addAll(scope.getAffectedSourceSets());
    }
    return sets;
  }

  @NotNull
  @Override
  public Collection<String> getAffectedUnloadedModules() {
    Set<String> unloadedModules = new LinkedHashSet<>();
    for (final CompileScope compileScope : myScopes) {
      unloadedModules.addAll(compileScope.getAffectedUnloadedModules());
    }
    return unloadedModules;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    for (CompileScope compileScope : myScopes) {
      T userData = compileScope.getUserData(key);
      if (userData != null) {
        return userData;
      }
    }
    return super.getUserData(key);
  }

  @NotNull
  public Collection<CompileScope> getScopes() {
    return Collections.unmodifiableList(myScopes);
  }
}
