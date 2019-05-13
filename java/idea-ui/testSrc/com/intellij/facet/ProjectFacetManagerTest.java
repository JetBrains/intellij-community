// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetType;

/**
 * @author nik
 */
public class ProjectFacetManagerTest extends FacetTestCase {
  public void testHasFacets() {
    final ProjectFacetManager manager = ProjectFacetManager.getInstance(myProject);
    assertFalse(manager.hasFacets(MockFacetType.ID));

    final MockFacet facet = addFacet();
    assertTrue(manager.hasFacets(MockFacetType.ID));

    removeFacet(facet);
    assertFalse(manager.hasFacets(MockFacetType.ID));
  }
}
