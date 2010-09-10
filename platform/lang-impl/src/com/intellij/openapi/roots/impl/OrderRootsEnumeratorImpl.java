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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author nik
 */
public class OrderRootsEnumeratorImpl implements OrderRootsEnumerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderRootsEnumeratorImpl");
  private final OrderEnumeratorBase myOrderEnumerator;
  private final OrderRootType myRootType;
  private boolean myUsingCache;
  private NotNullFunction<OrderEntry, VirtualFile[]> myCustomRootProvider;
  private boolean myWithoutSelfModuleOutput;

  public OrderRootsEnumeratorImpl(OrderEnumeratorBase orderEnumerator, OrderRootType rootType) {
    myOrderEnumerator = orderEnumerator;
    myRootType = rootType;
  }

  @NotNull
  @Override
  public VirtualFile[] getRoots() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      if (cache != null) {
        final int flags = myOrderEnumerator.getFlags();
        final VirtualFile[] cached = cache.getCachedRoots(myRootType, flags);
        if (cached == null) {
          return cache.setCachedRoots(myRootType, flags, computeRootsUrls()).getFiles();
        }
        else {
          return cached;
        }
      }
    }

    return VfsUtil.toVirtualFileArray(computeRoots());
  }

  @NotNull
  @Override
  public String[] getUrls() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      if (cache != null) {
        final int flags = myOrderEnumerator.getFlags();
        String[] cached = cache.getCachedUrls(myRootType, flags);
        if (cached == null) {
          return cache.setCachedRoots(myRootType, flags, computeRootsUrls()).getUrls();
        }
        else {
          return cached;
        }
      }
    }
    return ArrayUtil.toStringArray(computeRootsUrls());
  }

  private void checkCanUseCache() {
    LOG.assertTrue(myCustomRootProvider == null, "Caching not supported for OrderRootsEnumerator with 'usingCustomRootProvider' option");
    LOG.assertTrue(!myWithoutSelfModuleOutput, "Caching not supported for OrderRootsEnumerator with 'withoutSelfModuleOutput' option");
  }

  private Collection<VirtualFile> computeRoots() {
    final Collection<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    myOrderEnumerator.forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          collectModuleRoots(((ModuleSourceOrderEntry)orderEntry).getRootModel(), result);
        }
        else if (orderEntry instanceof ModuleOrderEntry) {
          ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
          final Module module = moduleOrderEntry.getModule();
          if (module != null) {
            if (myOrderEnumerator.addCustomOutput(moduleOrderEntry, result)) {
              return true;
            }
            collectModuleRoots(myOrderEnumerator.getRootModel(module), result);
          }
        }
        else {
          Collections.addAll(result, myCustomRootProvider != null ? myCustomRootProvider.fun(orderEntry) : orderEntry.getFiles(myRootType));
        }
        return true;
      }
    });
    return result;
  }

  private Collection<String> computeRootsUrls() {
    final Collection<String> result = new LinkedHashSet<String>();
    myOrderEnumerator.forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          collectModuleRootsUrls(((ModuleSourceOrderEntry)orderEntry).getRootModel(), result);
        }
        else if (orderEntry instanceof ModuleOrderEntry) {
          ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
          final Module module = moduleOrderEntry.getModule();
          if (module != null) {
            if (myOrderEnumerator.addCustomOutputUrls(moduleOrderEntry, result)) {
              return true;
            }
            collectModuleRootsUrls(myOrderEnumerator.getRootModel(module), result);
          }
        }
        else {
          Collections.addAll(result, orderEntry.getUrls(myRootType));
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  @Override
  public PathsList getPathsList() {
    final PathsList list = new PathsList();
    collectPaths(list);
    return list;
  }

  @Override
  public void collectPaths(@NotNull PathsList list) {
    list.addVirtualFiles(getRoots());
  }

  @Override
  public OrderRootsEnumerator usingCache() {
    myUsingCache = true;
    return this;
  }

  @Override
  public OrderRootsEnumerator withoutSelfModuleOutput() {
    myWithoutSelfModuleOutput = true;
    return this;
  }

  @Override
  public OrderRootsEnumerator usingCustomRootProvider(@NotNull NotNullFunction<OrderEntry, VirtualFile[]> provider) {
    myCustomRootProvider = provider;
    return this;
  }

  private void collectModuleRoots(ModuleRootModel rootModel, Collection<VirtualFile> result) {
    final boolean productionOnly = myOrderEnumerator.isProductionOnly();
    if (myRootType.equals(OrderRootType.SOURCES)) {
      Collections.addAll(result, rootModel.getSourceRoots(!productionOnly));
    }
    else if (myRootType.equals(OrderRootType.CLASSES)) {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        if (myWithoutSelfModuleOutput && myOrderEnumerator.isMainModuleModel(rootModel)) {
          if (!productionOnly) {
            Collections.addAll(result, extension.getOutputRoots(false));
          }
        }
        else {
          Collections.addAll(result, extension.getOutputRoots(!productionOnly));
        }
      }
    }
  }

  private void collectModuleRootsUrls(ModuleRootModel rootModel, Collection<String> result) {
    final boolean productionOnly = myOrderEnumerator.isProductionOnly();
    if (myRootType.equals(OrderRootType.SOURCES)) {
      Collections.addAll(result, rootModel.getSourceRootUrls(!productionOnly));
    }
    else if (myRootType.equals(OrderRootType.CLASSES)) {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        if (myWithoutSelfModuleOutput && myOrderEnumerator.isMainModuleModel(rootModel)) {
          if (!productionOnly) {
            Collections.addAll(result, extension.getOutputRootUrls(false));
          }
        }
        else {
          Collections.addAll(result, extension.getOutputRootUrls(!productionOnly));
        }
      }
    }
  }

}
