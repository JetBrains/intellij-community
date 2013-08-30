/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 
 * @author Kirill Likhodedov
 */
public class MockProjectRootManager extends ProjectRootManager {

  List<VirtualFile> myContentRoots = new ArrayList<VirtualFile>();

  @NotNull
  @Override
  public VirtualFile[] getContentRoots() {
    VirtualFile[] roots = new VirtualFile[myContentRoots.size()];
    for (int i = 0; i < myContentRoots.size(); i++) {
      roots[i] = myContentRoots.get(i);
    }
    return roots;
  }

  @NotNull
  @Override
  public ProjectFileIndex getFileIndex() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile[] getContentRootsFromAllModules() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<String> getContentRootUrls() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile[] getContentSourceRoots() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<VirtualFile> getModuleSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    throw new UnsupportedOperationException("'getContentSourceRoots' not implemented in " + getClass().getName());
  }

  @Override
  public Sdk getProjectSdk() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getProjectSdkName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setProjectSdk(Sdk sdk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setProjectSdkName(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationCount() {
    throw new UnsupportedOperationException();
  }
}
