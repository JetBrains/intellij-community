// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FacetPointerImpl<F extends Facet> implements FacetPointer<F> {
  private final FacetPointersManagerImpl myManager;
  private String myModuleName;
  private String myFacetTypeId;
  private String myFacetName;
  private F myFacet;

  public FacetPointerImpl(FacetPointersManagerImpl manager, String id) {
    myManager = manager;
    final int i = id.indexOf('/');
    myModuleName = id.substring(0, i);

    final int j = id.lastIndexOf('/');
    myFacetTypeId = id.substring(i + 1, j);
    myFacetName = id.substring(j+1);
  }

  public FacetPointerImpl(FacetPointersManagerImpl manager, final @NotNull F facet) {
    myManager = manager;
    myFacet = facet;
    updateInfo(myFacet);
    registerDisposable();
  }

  public void refresh() {
    findAndSetFacet();

    if (myFacet != null) {
      updateInfo(myFacet);
    }
  }

  private void findAndSetFacet() {
    if (myFacet == null) {
      myFacet = findFacet();
      if (myFacet != null) {
        registerDisposable();
      }
    }
  }

  private void registerDisposable() {
    Disposer.register(myFacet, new Disposable() {
      @Override
      public void dispose() {
        myManager.dispose(FacetPointerImpl.this);
        myFacet = null;
      }
    });
  }

  private void updateInfo(final @NotNull F facet) {
    myModuleName = facet.getModule().getName();
    myFacetTypeId = facet.getType().getStringId();
    myFacetName = facet.getName();
  }

  @Override
  public @NotNull Project getProject() {
    return myManager.getProject();
  }

  @Override
  public F getFacet() {
    findAndSetFacet();
    return myFacet;
  }

  private @Nullable F findFacet() {
    final Module module = ModuleManager.getInstance(myManager.getProject()).findModuleByName(myModuleName);
    if (module == null) return null;

    final FacetType<F, ?> type = getFacetType();
    if (type == null) return null;

    return FacetManager.getInstance(module).findFacet(type.getId(), myFacetName);
  }

  @Override
  public @Nullable F findFacet(@NotNull ModulesProvider modulesProvider, @NotNull FacetsProvider facetsProvider) {
    final Module module = modulesProvider.getModule(myModuleName);
    if (module == null) return null;
    final FacetType<F, ?> type = getFacetType();
    if (type == null) return null;
    return facetsProvider.findFacet(module, type.getId(), myFacetName);
  }

  @Override
  public @NotNull String getModuleName() {
    return myModuleName;
  }

  @Override
  public @NotNull String getFacetName() {
    return myFacetName;
  }

  @Override
  public @NotNull String getId() {
    return FacetPointersManager.constructId(myModuleName, myFacetTypeId, myFacetName);
  }

  @Override
  public @NotNull String getFacetTypeId() {
    return myFacetTypeId;
  }

  @Override
  public @NotNull String getModuleName(@Nullable ModifiableModuleModel moduleModel) {
    if (moduleModel != null && myFacet != null) {
      final String newName = moduleModel.getNewName(myFacet.getModule());
      if (newName != null) {
        return newName;
      }
    }
    return myModuleName;
  }

  @Override
  public @NotNull String getFacetName(@NotNull ModulesProvider modulesProvider, @NotNull FacetsProvider facetsProvider) {
    if (myFacet != null) {
      return modulesProvider.getFacetModel(myFacet.getModule()).getFacetName(myFacet);
    }
    return myFacetName;
  }

  @Override
  public @Nullable FacetType<F, ?> getFacetType() {
    //noinspection unchecked
    return FacetTypeRegistry.getInstance().findFacetType(myFacetTypeId);
  }
}
