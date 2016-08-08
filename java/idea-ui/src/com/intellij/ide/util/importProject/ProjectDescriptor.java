/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class ProjectDescriptor {
  private List<ModuleDescriptor> myModules = Collections.emptyList();
  private List<LibraryDescriptor> myLibraries = Collections.emptyList();
  private Set<LibraryDescriptor> myLibrariesSet = Collections.emptySet();

  public List<ModuleDescriptor> getModules() {
    return myModules;
  }

  public List<LibraryDescriptor> getLibraries() {
    return myLibraries;
  }

  public void setModules(@NotNull List<ModuleDescriptor> modules) {
    myModules = modules;
  }

  public void setLibraries(List<LibraryDescriptor> libraries) {
    myLibraries = libraries;
    myLibrariesSet = null;
  }

  public boolean isLibraryChosen(LibraryDescriptor lib) {
    Set<LibraryDescriptor> available = myLibrariesSet;
    if (available == null) {
      available = new HashSet<>(myLibraries);
      myLibrariesSet = available;
    }
    return available.contains(lib);
  }
}
