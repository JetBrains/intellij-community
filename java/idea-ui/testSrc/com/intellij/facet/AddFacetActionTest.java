// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.impl.ui.actions.AddFacetToModuleAction;
import com.intellij.facet.mock.MockFacetConfiguration;
import com.intellij.facet.mock.MockFacetEditorFacade;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.mock.MockSubFacetType;

/**
 * @author nik
 */
public class AddFacetActionTest extends FacetTestCase {
  private MockFacetEditorFacade myEditorFacade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myEditorFacade = new MockFacetEditorFacade();
  }

  @Override
  protected void tearDown() throws Exception {
    myEditorFacade = null;
    super.tearDown();
  }

  public void testAddFacet() {
    assertTrue(isVisible(MockFacetType.getInstance()));
    assertFalse(isVisible(MockSubFacetType.getInstance()));

    final FacetInfo info = new FacetInfo(MockFacetType.getInstance(), "239", new MockFacetConfiguration(), null);
    myEditorFacade.getModel().addFacetInfo(info);
    assertTrue(isVisible(MockFacetType.getInstance()));
    assertFalse(isVisible(MockSubFacetType.getInstance()));

    myEditorFacade.setSelectedFacet(info);
    assertTrue(isVisible(MockFacetType.getInstance()));
    assertTrue(isVisible(MockSubFacetType.getInstance()));

    final FacetInfo subInfo = new FacetInfo(MockSubFacetType.getInstance(), "42", new MockFacetConfiguration(), info);
    myEditorFacade.getModel().addFacetInfo(subInfo);

    assertTrue(isVisible(MockFacetType.getInstance()));
    assertFalse(isVisible(MockSubFacetType.getInstance()));
  }

  private boolean isVisible(FacetType type) {
    return AddFacetToModuleAction.isVisible(myEditorFacade, type);
  }
}
