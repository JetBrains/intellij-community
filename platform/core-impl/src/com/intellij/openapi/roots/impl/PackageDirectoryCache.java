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

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class PackageDirectoryCache {
  private final MultiMap<String, VirtualFile> myRootsByPackagePrefix;
  private final Map<String, PackageInfo> myDirectoriesByPackageNameCache = ContainerUtil.newConcurrentMap();
  private final Set<String> myNonExistentPackages = ContainerUtil.newConcurrentSet();
  @SuppressWarnings("UnusedDeclaration")
  private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      myNonExistentPackages.clear();
    }
  });

  public PackageDirectoryCache(MultiMap<String, VirtualFile> rootsByPackagePrefix) {
    myRootsByPackagePrefix = rootsByPackagePrefix;
  }

  @NotNull
  public List<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName) {
    PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.<VirtualFile>emptyList() : info.myPackageDirectories;
  }

  @Nullable
  private PackageInfo getPackageInfo(@NotNull final String packageName) {
    PackageInfo info = myDirectoriesByPackageNameCache.get(packageName);
    if (info == null) {
      if (myNonExistentPackages.contains(packageName)) return null;

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
        }
      }

      for (VirtualFile file : myRootsByPackagePrefix.get(packageName)) {
        if (file.isDirectory()) {
          result.add(file);
        }
      }

      if (!result.isEmpty()) {
        myDirectoriesByPackageNameCache.put(packageName, info = new PackageInfo(packageName, result));
      } else {
        myNonExistentPackages.add(packageName);
      }
    }

    return info;
  }

  public Set<String> getSubpackageNames(@NotNull final String packageName) {
    final PackageInfo info = getPackageInfo(packageName);
    return info == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(info.mySubPackages.getValue().keySet());
  }

  private class PackageInfo {
    final String myQname;
    final List<VirtualFile> myPackageDirectories;
    final NotNullLazyValue<MultiMap<String, VirtualFile>> mySubPackages = new VolatileNotNullLazyValue<MultiMap<String, VirtualFile>>() {
      @NotNull
      @Override
      protected MultiMap<String, VirtualFile> compute() {
        MultiMap<String, VirtualFile> result = MultiMap.createLinked();
        for (VirtualFile directory : myPackageDirectories) {
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

    PackageInfo(String qname, List<VirtualFile> packageDirectories) {
      this.myQname = qname;
      this.myPackageDirectories = packageDirectories;
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
