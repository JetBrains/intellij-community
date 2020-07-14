// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ProjectWideFacetListenersTest extends FacetTestCase {
  public void testAddRemoveFacet() {
    final MyProjectWideFacetListener listener = new MyProjectWideFacetListener();
    final ProjectWideFacetListenersRegistry listenersRegistry = ProjectWideFacetListenersRegistry.getInstance(myProject);
    listenersRegistry.registerListener(MockFacetType.ID, listener);

    final MockFacet facet1 = addFacet("1");
    assertEquals("firstAdded;added[1];", listener.getEvents());

    final MockFacet facet2 = addFacet("2");
    assertEquals("added[2];", listener.getEvents());

    removeFacet(facet1);
    assertEquals("beforeRemoved[1];removed[1];", listener.getEvents());

    removeFacet(facet2);
    assertEquals("beforeRemoved[2];removed[2];allRemoved;", listener.getEvents());

    listenersRegistry.unregisterListener(MockFacetType.ID, listener);
  }

  public void testLoadModuleWithFacet() throws Exception {
    final MyProjectWideFacetListener listener = new MyProjectWideFacetListener();
    final ProjectWideFacetListenersRegistry listenersRegistry = ProjectWideFacetListenersRegistry.getInstance(myProject);
    listenersRegistry.registerListener(MockFacetType.ID, listener);

    assertEquals("", listener.getEvents());
    File imlFile = PathManagerEx.findFileUnderCommunityHome("java/java-tests/testData/facet/module/MyFacetModule.iml");
    Module module = WriteAction.compute(() -> ModuleManager.getInstance(myProject).loadModule(imlFile.toPath()));
    assertEquals("firstAdded;added[MyMockFacet];", listener.getEvents());

    ModuleManager.getInstance(myProject).disposeModule(module);
    assertEquals("beforeRemoved[MyMockFacet];removed[MyMockFacet];allRemoved;", listener.getEvents());

    listenersRegistry.unregisterListener(MockFacetType.ID, listener);
  }

  private static class MyProjectWideFacetListener implements ProjectWideFacetListener<MockFacet> {
    private final StringBuilder myBuilder = new StringBuilder();

    @Override
    public void firstFacetAdded() {
      myBuilder.append("firstAdded;");
    }

    @Override
    public void allFacetsRemoved() {
      myBuilder.append("allRemoved;");
    }

    @Override
    public void facetConfigurationChanged(@NotNull final MockFacet facet) {
    }

    @Override
    public void facetAdded(@NotNull final MockFacet facet) {
      myBuilder.append("added[").append(facet.getName()).append("];");
    }

    @Override
    public void beforeFacetRemoved(@NotNull final MockFacet facet) {
      myBuilder.append("beforeRemoved[").append(facet.getName()).append("];");
    }

    @Override
    public void facetRemoved(@NotNull final MockFacet facet) {
      myBuilder.append("removed[").append(facet.getName()).append("];");
    }

    public String getEvents() {
      final String s = myBuilder.toString();
      myBuilder.setLength(0);
      return s;
    }
  }
}
