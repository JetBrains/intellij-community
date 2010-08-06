/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class ModulesOrderEnumerator extends OrderEnumeratorBase {
  private Collection<? extends Module> myModules;

  public ModulesOrderEnumerator(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    super(null, project, null);
    myModules = modules;
  }

  @Override
  public void forEach(@NotNull Processor<OrderEntry> processor) {
    myRecursivelyExportedOnly = false;
    final THashSet<Module> processed = new THashSet<Module>();
    for (Module module : myModules) {
      processEntries(getRootModel(module), processor, processed, true);
    }
  }
}
