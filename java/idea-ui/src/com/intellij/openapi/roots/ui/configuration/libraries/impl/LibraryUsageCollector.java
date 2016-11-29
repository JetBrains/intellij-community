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
package com.intellij.openapi.roots.ui.configuration.libraries.impl;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class LibraryUsageCollector extends AbstractApplicationUsagesCollector {

  @NonNls private static final String GROUP_ID = "libraries";

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    final Set<LibraryKind> usedKinds = new HashSet<>();
    final Processor<Library> processor = library -> {
      usedKinds.addAll(LibraryPresentationManagerImpl.getLibraryKinds(library, null));
      return true;
    };
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager.getInstance(module).orderEntries().librariesOnly().forEachLibrary(processor);
    }

    final HashSet<UsageDescriptor> usageDescriptors = new HashSet<>();
    for (LibraryKind kind : usedKinds) {
      usageDescriptors.add(new UsageDescriptor(kind.getKindId(), 1));
    }
    return usageDescriptors;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);  }
}
