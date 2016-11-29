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
package com.intellij.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class PackagePrefixIndex {
  private static final Object LOCK = new Object();
  private MultiMap<String, Module> myMap;
  private final Project myProject;

  public PackagePrefixIndex(Project project) {
    myProject = project;
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(final ModuleRootEvent event) {
        synchronized (LOCK) {
          myMap = null;
        }
      }
    });
  }

  public Collection<String> getAllPackagePrefixes(@Nullable GlobalSearchScope scope) {
    MultiMap<String, Module> map = myMap;
    if (map != null) {
      return getAllPackagePrefixes(scope, map);
    }

    map = new MultiMap<>();
    for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (final ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (final SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
          final String prefix = folder.getPackagePrefix();
          if (StringUtil.isNotEmpty(prefix)) {
            map.putValue(prefix, module);
          }
        }
      }
    }

    synchronized (LOCK) {
      if (myMap == null) {
        myMap = map;
      }
      return getAllPackagePrefixes(scope, myMap);
    }
  }

  private static Collection<String> getAllPackagePrefixes(final GlobalSearchScope scope, final MultiMap<String, Module> map) {
    if (scope == null) return map.keySet();

    List<String> result = new SmartList<>();
    for (final String prefix : map.keySet()) {
      modules: for (final Module module : map.get(prefix)) {
        if (scope.isSearchInModuleContent(module)) {
          result.add(prefix);
          break modules;
        }
      }
    }
    return result;
  }
}
