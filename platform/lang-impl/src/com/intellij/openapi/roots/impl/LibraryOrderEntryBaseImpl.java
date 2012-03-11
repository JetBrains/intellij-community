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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryBaseImpl");
  protected final ProjectRootManagerImpl myProjectRootManagerImpl;
  @NotNull protected DependencyScope myScope = DependencyScope.COMPILE;
  @Nullable private RootProvider myCurrentlySubscribedRootProvider = null;

  LibraryOrderEntryBaseImpl(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl instanceImpl) {
    super(rootModel);
    myProjectRootManagerImpl = instanceImpl;
  }

  protected final void init() {
    updateFromRootProviderAndSubscribe();
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return getRootFiles(OrderRootType.CLASSES);
    }
    if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (!myScope.isForProductionCompile()) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return getRootFiles(OrderRootType.CLASSES);
    }
    return getRootFiles(type);
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType type) {
    LOG.assertTrue(!getRootModel().getModule().isDisposed());
    RootProvider rootProvider = getRootProvider();
    if (rootProvider == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return rootProvider.getUrls(OrderRootType.CLASSES);
    }
    if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (!myScope.isForProductionCompile()) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      return rootProvider.getUrls(OrderRootType.CLASSES);
    }
    return rootProvider.getUrls(type);
  }

  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? VirtualFile.EMPTY_ARRAY : filterDirectories(rootProvider.getFiles(type));
  }

  protected VirtualFile[] filterDirectories(@NotNull VirtualFile[] files) {
    List<VirtualFile> filtered = ContainerUtil.mapNotNull(files, new NullableFunction<VirtualFile, VirtualFile>() {
      @Override
      public VirtualFile fun(@NotNull VirtualFile file) {
        return file.isDirectory() ? file : null;
      }
    });
    return VfsUtil.toVirtualFileArray(filtered);
  }

  @Nullable
  protected abstract RootProvider getRootProvider();

  @NotNull
  @SuppressWarnings({"UnusedDeclaration"})
  public String[] getRootUrls(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? ArrayUtil.EMPTY_STRING_ARRAY : rootProvider.getUrls(type);
  }

  @Override
  @NotNull
  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  protected void updateFromRootProviderAndSubscribe() {
    getRootModel().makeExternalChange(new Runnable() {
      @Override
      public void run() {
        resubscribe(getRootProvider());
      }
    });
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(@Nullable RootProvider wrapper) {
    if (wrapper != null) {
      myProjectRootManagerImpl.subscribeToRootProvider(this, wrapper);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      myProjectRootManagerImpl.unsubscribeFromRootProvider(this, myCurrentlySubscribedRootProvider);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  @Override
  public void dispose() {
    unsubscribe();
    super.dispose();
  }
}
