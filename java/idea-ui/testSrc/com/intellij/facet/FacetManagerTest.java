// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.mock.MockSubFacetType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.FacetManagerState;


/**
 * @author nik
 */
public class FacetManagerTest extends FacetTestCase {
  public void testAddDeleteFacet() {
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

  public void testAddSubFacet() {
    final MockFacet mockFacet = addFacet();
    assertNull(getFacetManager().getFacetByType(mockFacet, MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(mockFacet, MockFacetType.ID));

    final Facet subFacet = addSubFacet(mockFacet, "sub");
    assertSame(subFacet, getFacetManager().getFacetByType(mockFacet, MockSubFacetType.ID));
    assertNull(getFacetManager().getFacetByType(mockFacet, MockFacetType.ID));
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
    FacetManagerState state = getFacetManager().getState();
    return XmlSerializer.serialize(state);
  }

  private void read(@Nullable Element element) {
    getFacetManager().loadState(element == null ? new FacetManagerState() : XmlSerializer.deserialize(element, FacetManagerState.class));
  }

  public void testExternalization() {
    final FacetManager manager = getFacetManager();
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
