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
package com.intellij.openapi.roots.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.LocateLibraryDialog;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectModelModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class IdeaProjectModelModifier extends ProjectModelModifier {
  @Override
  public boolean addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope) {
    ModuleRootModificationUtil.addDependency(from, to, scope, false);
    return true;
  }

  @Override
  public boolean addExternalLibraryDependency(@NotNull Module module,
                                              @NotNull ExternalLibraryDescriptor descriptor,
                                              @NotNull DependencyScope scope) {
    List<String> defaultRoots = descriptor.getLibraryClassesRoots();
    LocateLibraryDialog dialog = new LocateLibraryDialog(module, defaultRoots, descriptor.getPresentableName());
    List<String> classesRoots = dialog.showAndGetResult();
    if (!classesRoots.isEmpty()) {
      String libraryName = classesRoots.size() > 1 ? descriptor.getPresentableName() : null;
      List<String> urls = OrderEntryFix.refreshAndConvertToUrls(classesRoots);
      ModuleRootModificationUtil.addModuleLibrary(module, libraryName, urls, Collections.<String>emptyList(), scope);
    }
    return true;
  }
}
