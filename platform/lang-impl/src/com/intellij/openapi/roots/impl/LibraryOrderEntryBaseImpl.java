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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryBaseImpl");
  protected final ProjectRootManagerImpl myProjectRootManagerImpl;
  @NotNull protected DependencyScope myScope = DependencyScope.COMPILE;
  private final MyRootSetChangedListener myRootSetChangedListener = new MyRootSetChangedListener();
  private RootProvider myCurrentlySubscribedRootProvider = null;

  LibraryOrderEntryBaseImpl(RootModelImpl rootModel, ProjectRootManagerImpl instanceImpl) {
    super(rootModel);
    myProjectRootManagerImpl = instanceImpl;
  }

  protected final void init() {
    updateFromRootProviderAndSubscribe();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return getRootFiles(OrderRootType.CLASSES);
    }
    else if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (!myScope.isForProductionCompile()) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return getRootFiles(OrderRootType.CLASSES);
    }
    else if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myScope == DependencyScope.PROVIDED ? VirtualFile.EMPTY_ARRAY : getRootFiles(OrderRootType.CLASSES);
    }
    return getRootFiles(type);
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    LOG.assertTrue(!getRootModel().getModule().isDisposed());
    RootProvider rootProvider = getRootProvider();
    if (rootProvider == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return rootProvider.getUrls(OrderRootType.CLASSES);
    }
    else if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (!myScope.isForProductionCompile()) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      return rootProvider.getUrls(OrderRootType.CLASSES);
    }
    else if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myScope == DependencyScope.PROVIDED ? ArrayUtil.EMPTY_STRING_ARRAY : rootProvider.getUrls(OrderRootType.CLASSES);
    }
    return rootProvider.getUrls(type);
  }

  public VirtualFile[] getRootFiles(OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? VirtualFile.EMPTY_ARRAY : filterDirectories(rootProvider.getFiles(type));
  }

  private static VirtualFile[] filterDirectories(VirtualFile[] files) {
    List<VirtualFile> filtered = ContainerUtil.mapNotNull(files, new Function<VirtualFile, VirtualFile>() {
      public VirtualFile fun(VirtualFile file) {
        return file.isDirectory() ? file : null;
      }
    });
    return VfsUtil.toVirtualFileArray(filtered);
  }

  protected abstract RootProvider getRootProvider();

  @SuppressWarnings({"UnusedDeclaration"})
  public String[] getRootUrls(OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? ArrayUtil.EMPTY_STRING_ARRAY : rootProvider.getUrls(type);
  }

  @NotNull
  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  protected void updateFromRootProviderAndSubscribe() {
    getRootModel().makeExternalChange(new Runnable() {
      public void run() {
        resubscribe(getRootProvider());
      }
    });
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(RootProvider wrapper) {
    if (wrapper != null) {
      addListenerToWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }

  private void addListenerToWrapper(final RootProvider wrapper,
                                      final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.addRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      final RootProvider wrapper = myCurrentlySubscribedRootProvider;
      removeListenerFromWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  protected void removeListenerFromWrapper(final RootProvider wrapper,
                                           final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.removeRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  private class MyRootSetChangedListener implements RootProvider.RootSetChangedListener {
    public void rootSetChanged(RootProvider wrapper) {
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void dispose() {
    unsubscribe();
    super.dispose();
  }
}
