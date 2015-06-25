/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.impl.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
class RuntimeModuleDescriptorImpl implements RuntimeModuleDescriptor {
  private final String myId;
  private final List<String> myDependencies;
  private final List<? extends ResourceRoot> myResourceRoots;

  RuntimeModuleDescriptorImpl(String moduleId, List<? extends ResourceRoot> roots, List<String> dependencies) {
    myId = moduleId;
    myResourceRoots = roots;
    myDependencies = dependencies;
  }

  @NotNull
  @Override
  public RuntimeModuleId getModuleId() {
    return RuntimeModuleId.ideaModule(myId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId.equals(((RuntimeModuleDescriptorImpl)o).myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @NotNull
  @Override
  public List<RuntimeModuleId> getDependencies() {
    List<RuntimeModuleId> moduleIds = new ArrayList<RuntimeModuleId>();
    for (String dependency : myDependencies) {
      moduleIds.add(RuntimeModuleId.ideaModule(dependency));
    }
    return moduleIds;
  }

  @NotNull
  @Override
  public List<File> getModuleRoots() {
    List<File> roots = new ArrayList<File>();
    for (ResourceRoot root : myResourceRoots) {
      roots.add(root.getRootFile());
    }
    return roots;
  }

  @Nullable
  @Override
  public InputStream readFile(@NotNull String relativePath) throws IOException {
    return null;
  }
}
