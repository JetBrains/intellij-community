package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 * 
 * @author Eugene Zhuravlev
 *         Date: May 5, 2004
 */
public class TrackDependenciesScope extends UserDataHolderBase implements CompileScope{
  private final CompileScope myDelegate;

  public TrackDependenciesScope(CompileScope delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly) {
    return myDelegate.getFiles(fileType, inSourceOnly);
  }

  public boolean belongs(String url) {
    return myDelegate.belongs(url);
  }

  @NotNull
  public Module[] getAffectedModules() {
    final Module[] affectedModules = myDelegate.getAffectedModules();
    // the dependencies between files may span several modules, so dependent modules might be affected
    final Set<Module> modules = new HashSet<Module>();
    for (final Module module : affectedModules) {
      modules.add(module);
      addDependentModules(module, modules);
    }
    return modules.toArray(new Module[modules.size()]);
  }

  private static void addDependentModules(Module module, Collection<Module> modules) {
    final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (final Module dependency : dependencies) {
      if (!modules.contains(dependency)) {
        modules.add(dependency); // avoid endless loops in casae of cyclic deps
        addDependentModules(dependency, modules);
      }
    }
  }

  public <T> T getUserData(final Key<T> key) {
    T userData = myDelegate.getUserData(key);
    if (userData != null) {
      return userData;
    }
    return super.getUserData(key);
  }
}
