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

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.ExportableUserDataHolderBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CompositeScope extends ExportableUserDataHolderBase implements CompileScope{
  private final List<CompileScope> myScopes = new ArrayList<>();

  public CompositeScope(CompileScope scope1, CompileScope scope2) {
    addScope(scope1);
    addScope(scope2);
  }

  public CompositeScope(CompileScope[] scopes) {
    for (CompileScope scope : scopes) {
      addScope(scope);
    }
  }

  private void addScope(CompileScope scope) {
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

  @NotNull
  public VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly) {
    Set<VirtualFile> allFiles = new THashSet<>();
    for (CompileScope scope : myScopes) {
      final VirtualFile[] files = scope.getFiles(fileType, inSourceOnly);
      if (files.length > 0) {
        ContainerUtil.addAll(allFiles, files);
      }
    }
    return VfsUtil.toVirtualFileArray(allFiles);
  }

  public boolean belongs(String url) {
    for (CompileScope scope : myScopes) {
      if (scope.belongs(url)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Module[] getAffectedModules() {
    Set<Module> modules = new HashSet<>();
    for (final CompileScope compileScope : myScopes) {
      ContainerUtil.addAll(modules, compileScope.getAffectedModules());
    }
    return modules.toArray(new Module[modules.size()]);
  }

  @NotNull
  @Override
  public Collection<String> getAffectedUnloadedModules() {
    Set<String> unloadedModules = new LinkedHashSet<>();
    for (final CompileScope compileScope : myScopes) {
      ContainerUtil.addAll(unloadedModules, compileScope.getAffectedUnloadedModules());
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
  
  public Collection<CompileScope> getScopes() {
    return Collections.unmodifiableList(myScopes);
  }
}
