// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.PackageDirectoryCache;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class PackageDirectoryCacheImpl implements PackageDirectoryCache {
  private final @NotNull BiConsumer<? super @NotNull String, ? super @NotNull List<? super VirtualFile>> myFillDirectoriesByPackage;
  private final @NotNull BiPredicate<? super @NotNull VirtualFile, ? super @NotNull String> myPackageDirectoryFilter;
  private final Map<String, PackageInfo> myDirectoriesByPackageNameCache = new ConcurrentHashMap<>();
  private final Set<String> myNonExistentPackages = ContainerUtil.newConcurrentSet();

  public PackageDirectoryCacheImpl(@NotNull BiConsumer<? super @NotNull String, ? super @NotNull List<? super VirtualFile>> fillFilesAndDirectoriesByPackage,
                                   @NotNull BiPredicate<? super @NotNull VirtualFile, ? super @NotNull String> packageDirectoryFilter) {
    myFillDirectoriesByPackage = fillFilesAndDirectoriesByPackage;
    myPackageDirectoryFilter = packageDirectoryFilter;
  }

  public void clear() {
    myNonExistentPackages.clear();
    myDirectoriesByPackageNameCache.clear();
  }

  public static void addValidDirectories(@NotNull Collection<? extends VirtualFile> source, @NotNull List<? super VirtualFile> target) {
    for (VirtualFile file : source) {
      if (file.isDirectory() && file.isValid()) {
        target.add(file);
      }
    }
  }

  public void onLowMemory() {
    myNonExistentPackages.clear();
  }

  @Override
  public @NotNull List<VirtualFile> getDirectoriesByPackageName(final @NotNull String packageName) {
    PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.emptyList() : Collections.unmodifiableList(info.myPackageDirectories);
  }

  @Override
  public @NotNull List<VirtualFile> getFilesByPackageName(final @NotNull String packageName) {
    PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.emptyList() : Collections.unmodifiableList(info.myPackageFiles);
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

      myFillDirectoriesByPackage.accept(packageName, result);

      if (!result.isEmpty()) {
        Map<Boolean, List<VirtualFile>> map = result.stream().collect(Collectors.partitioningBy(VirtualFile::isDirectory));
        myDirectoriesByPackageNameCache.put(packageName, info = new PackageInfo(packageName, map.get(true), map.get(false)));
      }
      else {
        myNonExistentPackages.add(packageName);
      }
    }

    return info;
  }

  @Override
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

  private final class PackageInfo {
    final @NotNull String myQname;
    final @NotNull List<? extends VirtualFile> myPackageDirectories;
    final @NotNull List<? extends VirtualFile> myPackageFiles;
    final NotNullLazyValue<MultiMap<String, VirtualFile>> mySubPackages;

    PackageInfo(@NotNull String qname, 
                @NotNull List<? extends VirtualFile> packageDirectories,
                @NotNull List<? extends VirtualFile> packageFiles) {
      myQname = qname;
      myPackageDirectories = packageDirectories;
      myPackageFiles = packageFiles;
      mySubPackages = NotNullLazyValue.volatileLazy(() -> {
        MultiMap<String, VirtualFile> result = MultiMap.createLinked();
        for (VirtualFile directory : myPackageDirectories) {
          ProgressManager.checkCanceled();
          for (VirtualFile child : directory.getChildren()) {
            String childName = child.getName();
            String packageName = myQname.isEmpty() ? childName : myQname + "." + childName;
            if (child.isDirectory() && myPackageDirectoryFilter.test(child, packageName)) {
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
}
