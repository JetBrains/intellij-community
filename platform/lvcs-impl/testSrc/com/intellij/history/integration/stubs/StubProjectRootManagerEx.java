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

package com.intellij.history.integration.stubs;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class StubProjectRootManagerEx extends ProjectRootManagerEx {

  public void registerRootsChangeUpdater(CacheUpdater updater) {
    throw new UnsupportedOperationException();
  }

  public void unregisterRootsChangeUpdater(CacheUpdater updater) {
    throw new UnsupportedOperationException();
  }

  public void addProjectJdkListener(ProjectJdkListener listener) {
    throw new UnsupportedOperationException();
  }

  public void removeProjectJdkListener(ProjectJdkListener listener) {
    throw new UnsupportedOperationException();
  }

  public void beforeRootsChange(boolean filetypes) {
    throw new UnsupportedOperationException();
  }

  public void makeRootsChange(@NotNull Runnable runnable, boolean filetypes, boolean fireEvents) {
    throw new UnsupportedOperationException();
  }

  public void rootsChanged(boolean filetypes) {
    throw new UnsupportedOperationException();
  }

  public void mergeRootsChangesDuring(@NotNull Runnable r) {
    throw new UnsupportedOperationException();
  }

  public GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn) {
    throw new UnsupportedOperationException();
  }

  public GlobalSearchScope getScopeForJdk(final JdkOrderEntry jdkOrderEntry) {
    throw new UnsupportedOperationException();
  }

  public void clearScopesCachesForModules() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public ProjectFileIndex getFileIndex() {
    throw new UnsupportedOperationException();
  }

  public void addModuleRootListener(ModuleRootListener listener) {
    throw new UnsupportedOperationException();
  }

  public void addModuleRootListener(ModuleRootListener listener, Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  public void removeModuleRootListener(ModuleRootListener listener) {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getFilesFromAllModules(final OrderRootType type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OrderEnumerator orderEntries() {
    throw new UnsupportedOperationException("'orderEntries' not implemented in " + getClass().getName());
  }

  public VirtualFile[] getContentRootsFromAllModules() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getContentSourceRoots() {
    throw new UnsupportedOperationException();
  }

  public String getCompilerOutputUrl() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile getCompilerOutput() {
    throw new UnsupportedOperationException();
  }

  public void setCompilerOutputUrl(String compilerOutputUrl) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Sdk getProjectJdk() {
    throw new UnsupportedOperationException();
  }

  public String getProjectJdkName() {
    throw new UnsupportedOperationException();
  }

  public void setProjectJdk(@Nullable Sdk jdk) {
    throw new UnsupportedOperationException();
  }

  public void setProjectJdkName(String name) {
    throw new UnsupportedOperationException();
  }

  public void multiCommit(ModifiableRootModel[] rootModels) {
    throw new UnsupportedOperationException();
  }

  public void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels) {
    throw new UnsupportedOperationException();
  }

  public long getModificationCount() {
    throw new UnsupportedOperationException();
  }
}
