// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetEditorContext;
import com.intellij.facet.mock.MockFacetEditorTab;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.application.WriteAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectFacetsConfiguratorTest extends FacetTestCase {

  public void testAddFacet() {
    final ProjectFacetsConfigurator configurator = createConfigurator();
    assertFalse(configurator.getFacetModel(myModule) instanceof ModifiableFacetModel);

    final ModifiableFacetModel modifiableModel = configurator.getOrCreateModifiableModel(myModule);
    assertSame(modifiableModel, configurator.getOrCreateModifiableModel(myModule));
    assertSame(modifiableModel, configurator.getFacetModel(myModule));

    assertFalse(configurator.isModified());
    final MockFacet facet = createFacet();
    configurator.addFacetInfo(facet);
    modifiableModel.addFacet(facet);
    assertTrue(configurator.isModified());

    WriteAction.runAndWait(() -> configurator.commitFacets());
    assertFalse(configurator.isModified());

    final FacetModel model = configurator.getFacetModel(myModule);
    assertFalse(model instanceof ModifiableFacetModel);
    assertSame(facet, assertOneElement(model.getAllFacets()));
  }

  public void testEditFacetConfiguration() throws Exception {
    final MockFacet facet = addFacet();
    facet.getConfiguration().setData("239");

    final ProjectFacetsConfigurator configurator = createConfigurator();
    configurator.addFacetInfo(facet);
    final FacetEditorImpl editor = configurator.getOrCreateEditor(facet);
    assertSame(editor, configurator.getOrCreateEditor(facet));

    assertFalse(editor.isModified());
    final MockFacetEditorTab editorTab = facet.getConfiguration().getEditor();
    assertEquals("239", editorTab.getDataTextField());

    editorTab.setDataTextField("a");
    assertTrue(editor.isModified());

    configurator.applyEditors();
    assertEquals("a", facet.getConfiguration().getData());
    configurator.disposeEditors();
    assertNull(editorTab.getDataTextField());

  }

  private static ProjectFacetsConfigurator createConfigurator() {
    return new ProjectFacetsConfigurator(null, null) {
      @Override
      protected FacetEditorContext createContext(final @NotNull Facet facet, final @Nullable FacetEditorContext parentContext) {
        return new MockFacetEditorContext(facet);
      }
    };
  }
}
