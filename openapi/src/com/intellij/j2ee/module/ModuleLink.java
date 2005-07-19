/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.j2ee.module;

import com.intellij.openapi.module.Module;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Alexey Kudravtsev
 */
public abstract class ModuleLink extends ContainerElement {
  protected static Map<J2EEPackagingMethod, String> methodToDescription = new HashMap<J2EEPackagingMethod, String>();

  public ModuleLink(Module parentModule) {
    super(parentModule);
  }

  public abstract Module getModule();

  public abstract String getId();

  public abstract String getName();

  public static String getId(Module module) {
    return module == null ? "" : new File(module.getModuleFilePath()).getName();
  }
}
