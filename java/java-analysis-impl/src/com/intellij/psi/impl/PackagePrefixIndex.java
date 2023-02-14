// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener;
import com.intellij.workspaceModel.ide.WorkspaceModelTopics;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootPropertiesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public class PackagePrefixIndex {
  private static final Object LOCK = new Object();
  private MultiMap<String, Module> myMap;
  private final Project myProject;

  public PackagePrefixIndex(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(WorkspaceModelTopics.CHANGED, new WorkspaceModelChangeListener() {
      @Override
      public void changed(@NotNull VersionedStorageChange event) {
        MultiMap<String, Module> map;
        synchronized (LOCK) {
          map = myMap;
        }
        if (map != null) {
          for (EntityChange<JavaSourceRootPropertiesEntity> change : event.getChanges(JavaSourceRootPropertiesEntity.class)) {
            JavaSourceRootPropertiesEntity oldEntity = change.getOldEntity();
            if (oldEntity != null) {
              updateMap(oldEntity, event.getStorageBefore(), (prefix, module) -> map.remove(prefix, module));
            }
            JavaSourceRootPropertiesEntity newEntity = change.getNewEntity();
            if (newEntity != null) {
              updateMap(newEntity, event.getStorageAfter(), (prefix, module) -> map.putValue(prefix, module));
            }
          }
        }
      }
      
      private void updateMap(@NotNull JavaSourceRootPropertiesEntity entity, @NotNull EntityStorage storageAfter, @NotNull BiConsumer<? super String, ? super Module> updater) {
        String prefix = entity.getPackagePrefix();
        if (StringUtil.isNotEmpty(prefix)) {
          Module module = ModuleEntityUtils.findModule(entity.getSourceRoot().getContentRoot().getModule(), storageAfter);
          if (module != null) {
            updater.accept(prefix, module);
          }
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
