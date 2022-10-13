// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PackageDirectoryCache {
  private static final Logger LOG = Logger.getInstance(PackageDirectoryCache.class);
  private final MultiMap<String, VirtualFile> myRootsByPackagePrefix = MultiMap.create();
  private final Map<String, PackageInfo> myDirectoriesByPackageNameCache = new ConcurrentHashMap<>();
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

  public @NotNull List<VirtualFile> getDirectoriesByPackageName(final @NotNull String packageName) {
    PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.emptyList() : Collections.unmodifiableList(info.myPackageDirectories);
  }

  private @Nullable PackageInfo getPackageInfo(@NotNull String packageName) {
    PackageInfo info = myDirectoriesByPackageNameCache.get(packageName);
    if (info == null && !myNonExistentPackages.contains(packageName)) {
      if (packageName.length() > Registry.intValue("java.max.package.name.length", 100) || Strings.containsAnyChar(packageName, ";[/")) {
        return null;
      }

      List<VirtualFile> result = new SmartList<>();

      if (Strings.isNotEmpty(packageName) && !StringUtil.startsWithChar(packageName, '.')) {
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

  public @NotNull Set<String> getSubpackageNames(final @NotNull String packageName, @NotNull GlobalSearchScope scope) {
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

  public static @NotNull PackageDirectoryCache createCache(@NotNull List<? extends VirtualFile> roots) {
    MultiMap<String, VirtualFile> map = MultiMap.create();
    map.putValues("", roots);
    return new PackageDirectoryCache(map);
  }

  private final class PackageInfo {
    final @NotNull String myQname;
    final @NotNull List<? extends VirtualFile> myPackageDirectories;
    final NotNullLazyValue<MultiMap<String, VirtualFile>> mySubPackages;

    PackageInfo(@NotNull String qname, @NotNull List<? extends VirtualFile> packageDirectories) {
      myQname = qname;
      myPackageDirectories = packageDirectories;
      mySubPackages = NotNullLazyValue.volatileLazy(() -> {
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
      });
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
