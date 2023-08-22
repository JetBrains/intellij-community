// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.invalid;

import com.intellij.facet.impl.ui.MultipleFacetEditorHelperImpl;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.util.ui.ThreeStateCheckBox;

import javax.swing.*;

public final class MultipleInvalidFacetEditor extends MultipleFacetSettingsEditor {
  private final MultipleFacetEditorHelperImpl myHelper;
  private JPanel myMainPanel;
  private ThreeStateCheckBox myIgnoreFacetsCheckBox;

  public MultipleInvalidFacetEditor(FacetEditor[] editors) {
    myHelper = new MultipleFacetEditorHelperImpl();
    myHelper.bind(myIgnoreFacetsCheckBox, editors, editor -> editor.getEditorTab(InvalidFacetEditor.class).getIgnoreCheckBox());
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void disposeUIResources() {
    myHelper.unbind();
  }
}
