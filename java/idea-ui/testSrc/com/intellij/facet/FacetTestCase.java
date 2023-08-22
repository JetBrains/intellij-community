// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetConfiguration;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.mock.MockSubFacetType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.Nullable;

public abstract class FacetTestCase extends HeavyPlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FacetType.EP_NAME.getPoint().registerExtension(new MockFacetType(), getTestRootDisposable());
    FacetType.EP_NAME.getPoint().registerExtension(new MockSubFacetType(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      removeAllFacets();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void removeAllFacets() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      FacetManager manager = FacetManager.getInstance(module);
      ModifiableFacetModel model = manager.createModifiableModel();
      for (Facet<?> facet : manager.getAllFacets()) {
        model.removeFacet(facet);
      }
      commit(model);
    }
  }

  protected FacetManager getFacetManager() {
    return FacetManager.getInstance(myModule);
  }

  protected void commit(final ModifiableFacetModel model) {
    WriteAction.runAndWait(() -> model.commit());
    ((FacetManagerBase) getFacetManager()).checkConsistency();
  }

  protected MockFacet createFacet() {
    return createFacet("facet");
  }

  protected MockFacet createFacet(final String name) {
    return new MockFacet(myModule, name);
  }

  protected Facet<MockFacetConfiguration> createSubFacet(MockFacet parent, final String name) {
    return new Facet<>(MockSubFacetType.getInstance(), myModule, name, new MockFacetConfiguration(), parent);
  }

  protected void removeFacet(final Facet facet) {
    final ModifiableFacetModel model = getFacetManager().createModifiableModel();
    model.removeFacet(facet);
    commit(model);
  }

  protected MockFacet addFacet() {
    return addFacet("name");
  }

  protected MockFacet addFacet(String name) {
    return addFacet(createFacet(name));
  }

  protected MockFacet addFacet(final MockFacet facet) {
    return addFacet(facet, null);
  }

  protected MockFacet addFacet(final MockFacet facet, @Nullable ProjectModelExternalSource externalSource) {
    ModifiableFacetModel model = getFacetManager().createModifiableModel();
    model.addFacet(facet, externalSource);
    commit(model);
    return facet;
  }

  protected Facet<MockFacetConfiguration> addSubFacet(MockFacet parent, final String name) {
    return addSubFacet(parent, name, null);
  }

  protected Facet<MockFacetConfiguration> addSubFacet(MockFacet parent, final String name, @Nullable ProjectModelExternalSource externalSource) {
    ModifiableFacetModel model = getFacetManager().createModifiableModel();
    final Facet<MockFacetConfiguration> facet = createSubFacet(parent, name);
    model.addFacet(facet, externalSource);
    commit(model);
    return facet;
  }

  protected void renameFacet(final Facet facet, final String newName) {
    final FacetManager manager = getFacetManager();
    final ModifiableFacetModel model = manager.createModifiableModel();
    model.rename(facet, newName);
    commit(model);
  }
}
