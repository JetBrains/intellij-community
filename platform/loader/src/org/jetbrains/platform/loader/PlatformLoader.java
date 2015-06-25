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
package org.jetbrains.platform.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.impl.PlatformLoaderImpl;
import org.jetbrains.platform.loader.repository.PlatformRepository;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

/**
 * @author nik
 */
public abstract class PlatformLoader {
  private static class InstanceHolder {
    private static final PlatformLoader ourInstance = new PlatformLoaderImpl();
  }

  public static PlatformLoader getInstance() {
    return InstanceHolder.ourInstance;
  }

  @NotNull
  public abstract PlatformRepository getRepository();

  public static void main(String[] args) {
    RuntimeModuleDescriptor module = getInstance().getRepository().getRequiredModule(RuntimeModuleId.ideaModule("lang-api"));
    System.out.println(module.getModuleRoots());
    System.exit(0);
  }
}
