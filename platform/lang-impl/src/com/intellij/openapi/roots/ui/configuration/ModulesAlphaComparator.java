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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;

import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1
 * @author 2003
 */
public class ModulesAlphaComparator implements Comparator<Module>{
  public static ModulesAlphaComparator INSTANCE = new ModulesAlphaComparator();

  @Override
  public int compare(Module module1, Module module2) {
    final String name1 = module1.getName();
    final String name2 = module2.getName();
    return name1.compareToIgnoreCase(name2);
  }
}
