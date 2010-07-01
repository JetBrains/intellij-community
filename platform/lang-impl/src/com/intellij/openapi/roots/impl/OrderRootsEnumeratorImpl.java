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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class OrderRootsEnumeratorImpl implements OrderRootsEnumerator {
  private final OrderEnumeratorBase myOrderEnumerator;
  private final OrderRootType myRootType;

  public OrderRootsEnumeratorImpl(OrderEnumeratorBase orderEnumerator, OrderRootType rootType) {
    myOrderEnumerator = orderEnumerator;
    myRootType = rootType;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    collectRoots(result);
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

  private void collectRoots(final List<VirtualFile> list) {
    myOrderEnumerator.forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry orderEntry) {
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          collectModulePaths(((ModuleSourceOrderEntry)orderEntry).getRootModel(), list);
        }
        else if (orderEntry instanceof ModuleOrderEntry) {
          ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
          final Module module = moduleOrderEntry.getModule();
          if (module != null) {
            if (myOrderEnumerator.addCustomOutput(moduleOrderEntry, list)) {
              return true;
            }
            collectModulePaths(myOrderEnumerator.getRootModel(module), list);
          }
        }
        else {
          Collections.addAll(list, orderEntry.getFiles(myRootType));
        }
        return true;
      }
    });
  }

  private void collectModulePaths(ModuleRootModel rootModel, List<VirtualFile> list) {
    if (myRootType.equals(OrderRootType.SOURCES)) {
      if (myOrderEnumerator.isProductionOnly()) {
        ContentEntry[] contentEntries = rootModel.getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
          for (SourceFolder folder : contentEntry.getSourceFolders()) {
            VirtualFile root = folder.getFile();
            if (root != null && !folder.isTestSource()) {
              list.add(root);
            }
          }
        }
      }
      else {
        Collections.addAll(list, rootModel.getSourceRoots());
      }
    }
    else if (myRootType.equals(OrderRootType.CLASSES)) {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        VirtualFile testOutput = extension.getCompilerOutputPathForTests();
        if (myOrderEnumerator.isProductionOnly()) {
          testOutput = null;
        }

        if (testOutput != null) {
          list.add(testOutput);
        }
        VirtualFile output = extension.getCompilerOutputPath();
        if (output != null && !output.equals(testOutput)) {
          list.add(output);
        }
      }
    }
  }

}
