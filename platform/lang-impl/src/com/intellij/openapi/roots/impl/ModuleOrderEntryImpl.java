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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 */
public class ModuleOrderEntryImpl extends OrderEntryBaseImpl implements ModuleOrderEntry, WritableOrderEntry, ClonableOrderEntry {
  @NonNls public static final String ENTRY_TYPE = "module";
  @NonNls public static final String MODULE_NAME_ATTR = "module-name";
  @NonNls private static final String EXPORTED_ATTR = "exported";

  private final ModulePointer myModulePointer;
  private boolean myExported = false;
  @NotNull private DependencyScope myScope;

  ModuleOrderEntryImpl(@NotNull Module module, @NotNull RootModelImpl rootModel) {
    super(rootModel);
    myModulePointer = ModulePointerManager.getInstance(module.getProject()).create(module);
    myScope = DependencyScope.COMPILE;
  }

  ModuleOrderEntryImpl(@NotNull String moduleName, @NotNull RootModelImpl rootModel) {
    super(rootModel);
    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(moduleName);
    myScope = DependencyScope.COMPILE;
  }

  ModuleOrderEntryImpl(Element element, RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    final String moduleName = element.getAttributeValue(MODULE_NAME_ATTR);
    if (moduleName == null) {
      throw new InvalidDataException();
    }

    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(moduleName);
    myScope = DependencyScope.readExternal(element);
  }

  private ModuleOrderEntryImpl(ModuleOrderEntryImpl that, RootModelImpl rootModel) {
    super(rootModel);
    final ModulePointer thatModule = that.myModulePointer;
    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(thatModule.getModuleName());
    myExported = that.myExported;
    myScope = that.myScope;
  }

  @NotNull
  public Module getOwnerModule() {
    return getRootModel().getModule();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return getFiles(type, new HashSet<Module>());
  }

  @NotNull
  VirtualFile[] getFiles(OrderRootType type, Set<Module> processed) {
    Module myModule = myModulePointer.getModule();
    if (myModule != null && !processed.contains(myModule) && !myModule.isDisposed()) {
      processed.add(myModule);
      if (!myScope.isForProductionCompile() && type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
        return VirtualFile.EMPTY_ARRAY;
      }
      if (myScope == DependencyScope.PROVIDED && type == OrderRootType.CLASSES_AND_OUTPUT) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return ((ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule)).getFilesForOtherModules(type, processed);
    }
    else {
      return VirtualFile.EMPTY_ARRAY;
    }
  }

  @NotNull
  public String[] getUrls(OrderRootType rootType) {
    List<String> urls = getUrls(rootType, null);
    return ArrayUtil.toStringArray(urls);
  }

  public List<String> getUrls(OrderRootType rootType, @Nullable Set<Module> processed) {
    Module myModule = myModulePointer.getModule();
    if (myModule != null && !myModule.isDisposed() && (processed == null || !processed.contains(myModule))) {
      if (processed == null) processed = new THashSet<Module>();
      processed.add(myModule);
      if (!myScope.isForProductionCompile() && rootType == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
        return Collections.emptyList();
      }
      if (myScope == DependencyScope.PROVIDED && rootType == OrderRootType.CLASSES_AND_OUTPUT) {
        return Collections.emptyList();
      }
      return ((ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule)).getUrlsForOtherModules(rootType, processed);
    }
    return Collections.emptyList();
  }


  public boolean isValid() {
    return !isDisposed() && getModule() != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleOrderEntry(this, initialValue);
  }

  public String getPresentableName() {
    return getModuleName();
  }

  public boolean isSynthetic() {
    return false;
  }

  @Nullable
  public Module getModule() {
    return getRootModel().getConfigurationAccessor().getModule(myModulePointer.getModule(), myModulePointer.getModuleName());
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(MODULE_NAME_ATTR, getModuleName());
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    rootElement.addContent(element);
  }

  public String getModuleName() {
    return myModulePointer.getModuleName();
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleOrderEntryImpl(this, rootModel);
  }

  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean value) {
    getRootModel().assertWritable();
    myExported = value;
  }

  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(@NotNull DependencyScope scope) {
    getRootModel().assertWritable();
    myScope = scope;
  }
}
