/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public <T> T getUserData(@NotNull final Key<T> key) {
    T userData = myDelegate.getUserData(key);
    if (userData != null) {
      return userData;
    }
    return super.getUserData(key);
  }
}
