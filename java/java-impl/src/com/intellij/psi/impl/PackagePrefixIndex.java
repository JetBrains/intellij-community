// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
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
    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull final ModuleRootEvent event) {
        synchronized (LOCK) {
          myMap = null;
        }
      }
    });
  }

  public Collection<String> getAllPackagePrefixes(@Nullable GlobalSearchScope scope) {
    MultiMap<String, Module> map;
    synchronized (LOCK) {
      map = myMap;
    }
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
