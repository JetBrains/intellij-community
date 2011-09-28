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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Library entry for module ("in-place") libraries
 *  @author dsl
 */
public class ModuleLibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryImpl");
  private final Library myLibrary;
  @NonNls public static final String ENTRY_TYPE = "module-library";
  private boolean myExported;
  @NonNls public static final String EXPORTED_ATTR = "exported";

  //cloning
  private ModuleLibraryOrderEntryImpl(Library library, RootModelImpl rootModel, boolean isExported, DependencyScope scope) {
    super(rootModel, ProjectRootManagerImpl.getInstanceImpl(rootModel.getProject()));
    myLibrary = ((LibraryEx)library).cloneLibrary(getRootModel());
    doinit();
    myExported = isExported;
    myScope = scope;
  }

  ModuleLibraryOrderEntryImpl(String name, final LibraryType<?> type, RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    myLibrary = LibraryTableImplUtil.createModuleLevelLibrary(name, type, getRootModel());
    doinit();
  }

  ModuleLibraryOrderEntryImpl(Element element, RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    myLibrary = LibraryTableImplUtil.loadLibrary(element, getRootModel());
    doinit();
  }

  private void doinit() {
    Disposer.register(this, myLibrary);
    init();
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  protected RootProvider getRootProvider() {
    return myLibrary.getRootProvider();
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public boolean isModuleLevel() {
    return true;
  }

  public String getLibraryName() {
    return myLibrary.getName();
  }

  public String getLibraryLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  public String getPresentableName() {
    final String name = myLibrary.getName();
    if (name != null) {
      return name;
    }
    else {
      if (myLibrary instanceof LibraryEx && ((LibraryEx)myLibrary).isDisposed()) {
        return "<unknown>";
      }

      final String[] urls = myLibrary.getUrls(OrderRootType.CLASSES);
      if (urls.length > 0) {
        String url = urls[0];
        return PathUtil.toPresentableUrl(url);
      }
      else {
        return null;
      }
    }
  }

  public boolean isValid() {
    return !isDisposed() && myLibrary != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  public boolean isSynthetic() {
    return true;
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleLibraryOrderEntryImpl(myLibrary, rootModel, myExported, myScope);
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    myLibrary.writeExternal(element);
    rootElement.addContent(element);
  }


  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean value) {
    myExported = value;
  }

  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }
}
