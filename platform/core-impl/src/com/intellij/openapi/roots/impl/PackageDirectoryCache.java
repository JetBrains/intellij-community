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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class PackageDirectoryCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PackageDirectoryCache");
  private final MultiMap<String, VirtualFile> myRootsByPackagePrefix = MultiMap.create();
  private final Map<String, PackageInfo> myDirectoriesByPackageNameCache = ContainerUtil.newConcurrentMap();
  private final Set<String> myNonExistentPackages = ContainerUtil.newConcurrentSet();

  public PackageDirectoryCache(@NotNull MultiMap<String, VirtualFile> rootsByPackagePrefix) {
    for (String prefix : rootsByPackagePrefix.keySet()) {
      for (VirtualFile file : rootsByPackagePrefix.get(prefix)) {
        if (!file.isValid()) {
          LOG.error("Invalid root: " + file);
        }
        else {
          myRootsByPackagePrefix.putValue(prefix, file);
        }
      }
    }
  }

  void clear() {
    myNonExistentPackages.clear();
    myDirectoriesByPackageNameCache.clear();
  }

  public void onLowMemory() {
    myNonExistentPackages.clear();
  }

  @NotNull
  public List<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName) {
    PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.emptyList() : Collections.unmodifiableList(info.myPackageDirectories);
  }

  @Nullable
  private PackageInfo getPackageInfo(@NotNull final String packageName) {
    PackageInfo info = myDirectoriesByPackageNameCache.get(packageName);
    if (info == null && !myNonExistentPackages.contains(packageName)) {
      if (packageName.length() > Registry.intValue("java.max.package.name.length") || StringUtil.containsAnyChar(packageName, ";[/")) {
        return null;
      }

      List<VirtualFile> result = ContainerUtil.newSmartList();

      if (StringUtil.isNotEmpty(packageName) && !StringUtil.startsWithChar(packageName, '.')) {
        int i = packageName.lastIndexOf('.');
        while (true) {
          PackageInfo parentInfo = getPackageInfo(i > 0 ? packageName.substring(0, i) : "");
          if (parentInfo != null) {
            result.addAll(parentInfo.getSubPackageDirectories(packageName.substring(i + 1)));
          }
          if (i < 0) break;
          i = packageName.lastIndexOf('.', i - 1);
          ProgressManager.checkCanceled();
        }
      }

      for (VirtualFile file : myRootsByPackagePrefix.get(packageName)) {
        if (file.isDirectory() && file.isValid()) {
          result.add(file);
        }
      }

      if (!result.isEmpty()) {
        myDirectoriesByPackageNameCache.put(packageName, info = new PackageInfo(packageName, result));
      }
      else {
        myNonExistentPackages.add(packageName);
      }
    }

    return info;
  }

  @NotNull
  public Set<String> getSubpackageNames(@NotNull final String packageName) {
    final PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.emptySet() : Collections.unmodifiableSet(info.mySubPackages.getValue().keySet());
  }

  @NotNull
  public Set<String> getSubpackageNames(@NotNull final String packageName, @NotNull GlobalSearchScope scope) {
    final PackageInfo info = getPackageInfo(packageName);
    if (info == null) return Collections.emptySet();

    final Set<String> result = new HashSet<>();
    for (Map.Entry<String, Collection<VirtualFile>> entry : info.mySubPackages.getValue().entrySet()) {
      final String shortName = entry.getKey();
      final Collection<VirtualFile> directories = entry.getValue();
      if (ContainerUtil.exists(directories, scope::contains)) {
        result.add(shortName);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  @NotNull
  public static PackageDirectoryCache createCache(@NotNull List<? extends VirtualFile> roots) {
    MultiMap<String, VirtualFile> map = MultiMap.create();
    map.putValues("", roots);
    return new PackageDirectoryCache(map);
  }

  private class PackageInfo {
    @NotNull
    final String myQname;
    @NotNull
    final List<? extends VirtualFile> myPackageDirectories;
    final NotNullLazyValue<MultiMap<String, VirtualFile>> mySubPackages = new VolatileNotNullLazyValue<MultiMap<String, VirtualFile>>() {
      @NotNull
      @Override
      protected MultiMap<String, VirtualFile> compute() {
        MultiMap<String, VirtualFile> result = MultiMap.createLinked();
        for (VirtualFile directory : myPackageDirectories) {
          ProgressManager.checkCanceled();
          for (VirtualFile child : directory.getChildren()) {
            String childName = child.getName();
            String packageName = myQname.isEmpty() ? childName : myQname + "." + childName;
            if (child.isDirectory() && isPackageDirectory(child, packageName)) {
              result.putValue(childName, child);
            }
          }
        }
        return result;
      }
    };

    PackageInfo(@NotNull String qname, @NotNull List<? extends VirtualFile> packageDirectories) {
      myQname = qname;
      myPackageDirectories = packageDirectories;
    }

    @NotNull
    Collection<VirtualFile> getSubPackageDirectories(String shortName) {
      return mySubPackages.getValue().get(shortName);
    }
  }

  protected boolean isPackageDirectory(@NotNull VirtualFile dir, @NotNull String packageName) {
    return true;
  }
}
