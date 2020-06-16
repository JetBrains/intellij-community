// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.ProjectTopics;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetConfiguration;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.mock.MockSubFacetType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.FacetManagerState;
import org.junit.Assume;

import java.io.File;


public class FacetManagerTest extends FacetTestCase {
  public void testAddDeleteFacet() {
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        fail("rootsChanged must not be called on change of facets");
      }
    });
    final FacetManager manager = getFacetManager();
    assertEquals(0, manager.getAllFacets().length);
    assertEquals(0, manager.getFacetsByType(MockFacetType.ID).size());
    assertNull(manager.getFacetByType(MockFacetType.ID));

    ModifiableFacetModel model = manager.createModifiableModel();
    final MockFacet facet = createFacet();
    model.addFacet(facet);
    assertTrue(model.isNewFacet(facet));

    assertEquals(0, manager.getAllFacets().length);
    assertSame(facet, assertOneElement(model.getAllFacets()));
    assertSame(facet, assertOneElement(model.getFacetsByType(MockFacetType.ID)));
    assertFalse(facet.isInitialized());

    commit(model);
    assertTrue(facet.isInitialized());
    assertSame(facet, assertOneElement(manager.getAllFacets()));
    assertSame(facet, assertOneElement(manager.getFacetsByType(MockFacetType.ID)));

    model = manager.createModifiableModel();
    assertFalse(model.isNewFacet(facet));
    model.removeFacet(facet);
    assertFalse(facet.isDisposed());
    assertFalse(model.isNewFacet(facet));
    commit(model);
    assertEquals(0, manager.getAllFacets().length);
    assertEquals(0, manager.getFacetsByType(MockFacetType.ID).size());
    assertTrue(facet.isDisposed());
  }

  public void testAddFacetToNotYetCommittedModule() {
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    Module newModule = moduleModel.newModule(new File(myProject.getBasePath(), "new.iml").getAbsolutePath(), EmptyModuleType.EMPTY_MODULE);
    FacetManager manager = FacetManager.getInstance(newModule);
    assertNull(manager.getFacetByType(MockFacetType.ID));

    ModifiableFacetModel model = manager.createModifiableModel();
    final MockFacet facet = new MockFacet(newModule, "mock");
    model.addFacet(facet);
    assertSame(facet, assertOneElement(model.getAllFacets()));
    assertSame(facet, assertOneElement(model.getFacetsByType(MockFacetType.ID)));
    commit(model);
    assertSame(facet, assertOneElement(manager.getAllFacets()));
    assertSame(facet, assertOneElement(manager.getFacetsByType(MockFacetType.ID)));

    WriteAction.runAndWait(() -> moduleModel.commit());
    assertSame(facet, assertOneElement(manager.getAllFacets()));
    assertSame(facet, assertOneElement(manager.getFacetsByType(MockFacetType.ID)));
  }

  public void testAddRemoveSubFacet() {
    final MockFacet mockFacet = addFacet();
    assertNull(getFacetManager().getFacetByType(mockFacet, MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(mockFacet, MockFacetType.ID));

    final Facet subFacet = addSubFacet(mockFacet, "sub");
    assertSame(subFacet, getFacetManager().getFacetByType(mockFacet, MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(mockFacet, MockFacetType.ID));

    ModifiableFacetModel model = getFacetManager().createModifiableModel();
    model.removeFacet(subFacet);
    commit(model);

    assertNull(getFacetManager().getFacetByType(MockSubFacetType.ID));
    assertSame(mockFacet, getFacetManager().getFacetByType(MockFacetType.ID));

    model = getFacetManager().createModifiableModel();
    model.removeFacet(mockFacet);
    commit(model);
    assertNull(getFacetManager().getFacetByType(MockFacetType.ID));
  }

  public void testChangeFacetConfiguration() {
    String configData = "data";
    ModifiableFacetModel model = getFacetManager().createModifiableModel();
    MockFacet mockFacet = new MockFacet(myModule, "mock");
    model.addFacet(mockFacet);
    mockFacet.getConfiguration().setData(configData);
    commit(model);

    MockFacet facetByType = getFacetManager().getFacetByType(MockFacetType.ID);
    assertEquals(configData, facetByType.getConfiguration().getData());
    assertSame(mockFacet, facetByType);
  }
  
  public void testAddRemoveFacetWithSubFacet() {
    assertNull(getFacetManager().getFacetByType(MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(MockFacetType.ID));

    ModifiableFacetModel model = getFacetManager().createModifiableModel();
    MockFacet mockFacet = new MockFacet(myModule, "mock");
    model.addFacet(mockFacet);
    Facet subFacet = MockSubFacetType.getInstance().createFacet(myModule, "mock", new MockFacetConfiguration(), mockFacet);
    model.addFacet(subFacet);
    commit(model);

    assertSame(subFacet, getFacetManager().getFacetByType(mockFacet, MockSubFacetType.ID));
    assertSame(mockFacet, getFacetManager().getFacetByType(MockFacetType.ID));
    assertNull(getFacetManager().getFacetByType(mockFacet, MockFacetType.ID));

    model = getFacetManager().createModifiableModel();
    model.removeFacet(subFacet);
    model.removeFacet(mockFacet);
    commit(model);

    assertNull(getFacetManager().getFacetByType(MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(MockFacetType.ID));
  }

  public void testRenameNewFacet() {
    final FacetManager manager = getFacetManager();
    final ModifiableFacetModel model = manager.createModifiableModel();
    final MockFacet facet = createFacet();
    model.addFacet(facet);
    model.rename(facet, "newName");
    commit(model);

    assertEquals("newName", assertOneElement(manager.getAllFacets()).getName());
  }

  public void testRenameExistingFacet() {
    final MockFacet mockFacet = addFacet("facet");

    assertEquals("facet", mockFacet.getName());
    renameFacet(mockFacet, "newName");
    assertEquals("newName", mockFacet.getName());
  }

  public void testListeners() {
    final FacetManager manager = getFacetManager();
    final MyFacetManagerListener listener = new MyFacetManagerListener();
    myModule.getMessageBus().connect(/*getTestRootDisposable()*/).subscribe(FacetManager.FACETS_TOPIC, listener);

    ModifiableFacetModel model = manager.createModifiableModel();
    final MockFacet facet = new MockFacet(myModule, "1");
    model.addFacet(facet);
    assertEquals("", listener.getEvents());
    commit(model);
    assertEquals("before added[1]\nadded[1]\n", listener.getEvents());

    model = manager.createModifiableModel();
    model.rename(facet, "3");
    assertEquals("", listener.getEvents());
    commit(model);
    assertEquals("before renamed[1]\nrenamed[3]\n", listener.getEvents());

    model = manager.createModifiableModel();
    model.removeFacet(facet);
    commit(model);
    assertEquals("before removed[3]\nremoved[3]\n", listener.getEvents());

    model = manager.createModifiableModel();
    final MockFacet facet2 = new MockFacet(myModule, "2");
    model.addFacet(facet2);
    model.removeFacet(facet2);
    commit(model);
    assertTrue(listener.getEvents().isEmpty());
  }

  @Nullable
  private Element write() {
    FacetManagerState state = ((FacetManagerImpl)getFacetManager()).getState();
    return XmlSerializer.serialize(state);
  }

  private void read(@Nullable Element element) {
    ((FacetManagerImpl)getFacetManager()).loadState(element == null ? new FacetManagerState() : XmlSerializer.deserialize(element, FacetManagerState.class));
  }

  public void testExternalization() {
    FacetManager manager = getFacetManager();
    Assume.assumeTrue("Not applicable to workspace model", manager instanceof FacetManagerImpl);
    assertNull(write());

    addFacet();

    writeAndRead();

    final Facet facet = assertOneElement(manager.getAllFacets());
    assertSame(MockFacetType.getInstance(), facet.getType());

    addSubFacet((MockFacet)facet, "subfacet");

    writeAndRead();

    final Facet[] facets = manager.getSortedFacets();
    assertEquals(2, facets.length);
    assertSame(MockFacetType.getInstance(), facets[0].getType());
    assertSame(MockSubFacetType.getInstance(), facets[1].getType());
    assertSame(facets[0], facets[1].getUnderlyingFacet());

    addFacet("facet3");
    writeAndRead();
    assertEquals(3, manager.getAllFacets().length);
    assertOneElement(manager.getFacetsByType(MockSubFacetType.ID));
  }

  private void writeAndRead() {
    read(write());
  }

  public void testGetSortedFacets() {
    final MockFacet facet = addFacet();
    assertOrderedEquals(getFacetManager().getSortedFacets(), facet);
    final Facet subFacet = addSubFacet(facet, "subfacet");
    assertOrderedEquals(getFacetManager().getSortedFacets(), facet, subFacet);
  }

  private static class MyFacetManagerListener implements FacetManagerListener {
    private final StringBuilder myEvents = new StringBuilder();

    @Override
    public void beforeFacetAdded(@NotNull Facet facet) {
      myEvents.append("before added[").append(facet.getName()).append("]\n");
    }

    @Override
    public void beforeFacetRemoved(@NotNull Facet facet) {
      myEvents.append("before removed[").append(facet.getName()).append("]\n");
    }

    @Override
    public void beforeFacetRenamed(@NotNull final Facet facet) {
      myEvents.append("before renamed[").append(facet.getName()).append("]\n");
    }

    @Override
    public void facetRenamed(@NotNull final Facet facet, @NotNull final String oldName) {
      myEvents.append("renamed[").append(facet.getName()).append("]\n");
    }

    @Override
    public void facetConfigurationChanged(@NotNull final Facet facet) {
      myEvents.append("changed[").append(facet.getName()).append("]\n");
    }

    @Override
    public void facetAdded(@NotNull Facet facet) {
      myEvents.append("added[").append(facet.getName()).append("]\n");
    }

    @Override
    public void facetRemoved(@NotNull Facet facet) {
      myEvents.append("removed[").append(facet.getName()).append("]\n");
    }

    public String getEvents() {
      final String s = myEvents.toString();
      myEvents.setLength(0);
      return s;
    }
  }
}
