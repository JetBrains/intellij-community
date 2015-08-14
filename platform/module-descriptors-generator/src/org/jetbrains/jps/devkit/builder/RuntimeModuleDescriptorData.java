/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.builder;

import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class RuntimeModuleDescriptorData {
  private final RuntimeModuleId myId;
  private final List<String> myModuleRoots;
  private final List<RuntimeModuleId> myDependencies;

  public RuntimeModuleDescriptorData(RuntimeModuleId id, List<String> moduleRoots) {
    this(id, moduleRoots, Collections.<RuntimeModuleId>emptyList());
  }

  public RuntimeModuleDescriptorData(RuntimeModuleId id,
                                     List<String> moduleRoots,
                                     List<RuntimeModuleId> dependencies) {
    myId = id;
    myModuleRoots = moduleRoots;
    myDependencies = dependencies;
  }



  public RuntimeModuleId getModuleId() {
    return myId;
  }

  public List<String> getModuleRoots() {
    return myModuleRoots;
  }

  public List<RuntimeModuleId> getDependencies() {
    return myDependencies;
  }
}
