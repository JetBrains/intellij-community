package com.intellij.history.integration.stubs;

import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubProjectRootManagerEx extends ProjectRootManagerEx {
  public void setLanguageLevel(LanguageLevel level) {
    throw new UnsupportedOperationException();
  }

  public LanguageLevel getLanguageLevel() {
    throw new UnsupportedOperationException();
  }

  public void registerChangeUpdater(CacheUpdater updater) {
    throw new UnsupportedOperationException();
  }

  public void unregisterChangeUpdater(CacheUpdater updater) {
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

  public void rootsChanged(boolean filetypes) {
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

  public VirtualFile[] getRootFiles(ProjectRootType type) {
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

  public VirtualFile[] getFullClassPath() {
    throw new UnsupportedOperationException();
  }

  public ProjectJdk getJdk() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public ProjectJdk getProjectJdk() {
    throw new UnsupportedOperationException();
  }

  public String getProjectJdkName() {
    throw new UnsupportedOperationException();
  }

  public void setProjectJdk(@Nullable ProjectJdk jdk) {
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

  public void checkCircularDependency(ModifiableRootModel[] rootModels, ModifiableModuleModel moduleModel)
    throws ModuleCircularDependencyException {
    throw new UnsupportedOperationException();
  }

  public long getModificationCount() {
    throw new UnsupportedOperationException();
  }
}
