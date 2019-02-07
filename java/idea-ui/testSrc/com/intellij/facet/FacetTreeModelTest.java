// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.mock.MockFacetConfiguration;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.testFramework.UsefulTestCase;

/**
 * @author nik
 */
public class FacetTreeModelTest extends UsefulTestCase {
  private FacetTreeModel myModel;

  public void testTopLevelFacets() {
    FacetInfo f1 = create();
    FacetInfo f2 = create();
    FacetInfo f3 = create();

    assertOrderedEquals(myModel.getTopLevelFacets(), f1, f2, f3);
    assertTrue(myModel.getChildren(f1).isEmpty());
    assertTrue(myModel.getChildren(f2).isEmpty());
    assertTrue(myModel.getChildren(f3).isEmpty());
    assertNull(myModel.getParent(f1));
    assertNull(myModel.getParent(f2));
    assertNull(myModel.getParent(f3));
    assertTrue(myModel.hasFacetOfType(null, MockFacetType.ID));
    assertFalse(myModel.hasFacetOfType(f1, MockFacetType.ID));

    assertSame(f2, myModel.findNearestFacet(f1));
    assertSame(f3, myModel.findNearestFacet(f2));
    assertSame(f2, myModel.findNearestFacet(f3));

    myModel.removeFacetInfo(f2);
    assertOrderedEquals(myModel.getTopLevelFacets(), f1, f3);
    assertSame(f3, myModel.findNearestFacet(f1));
    assertSame(f1, myModel.findNearestFacet(f3));

    myModel.removeFacetInfo(f1);
    assertOrderedEquals(myModel.getTopLevelFacets(), f3);
    assertNull(myModel.findNearestFacet(f3));
  }

  public void testNestedFacets() {
    FacetInfo f1 = create();
    FacetInfo f1a = create(f1);
    FacetInfo f1b = create(f1);
    FacetInfo f1a1 = create(f1a);
    FacetInfo f2 = create();
    assertOrderedEquals(myModel.getTopLevelFacets(), f1, f2);
    assertOrderedEquals(myModel.getChildren(f1), f1a, f1b);
    assertOrderedEquals(myModel.getChildren(f1a), f1a1);
    assertOrderedEquals(myModel.getChildren(f2));
    assertOrderedEquals(myModel.getChildren(f1b));

    assertNull(myModel.getParent(f1));
    assertNull(myModel.getParent(f2));
    assertSame(f1, myModel.getParent(f1a));
    assertSame(f1, myModel.getParent(f1b));
    assertSame(f1a, myModel.getParent(f1a1));

    assertTrue(myModel.hasFacetOfType(f1, MockFacetType.ID));
    assertSame(f1a, myModel.findNearestFacet(f1a1));
    assertSame(f1a, myModel.findNearestFacet(f1a1));
  }

  private FacetInfo create(FacetInfo underlying) {
    final FacetInfo info = new FacetInfo(MockFacetType.getInstance(), "239", new MockFacetConfiguration(), underlying);
    myModel.addFacetInfo(info);
    return info;
  }

  private FacetInfo create() {
    return create(null);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = new FacetTreeModel();
  }


  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    super.tearDown();
  }
}
