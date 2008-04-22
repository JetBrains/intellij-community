package com.intellij.facet.ui;

import javax.swing.*;

/**
 * Provides component to edit several facets simultaneously. Changes in this component must be porpogated to original facet editors.
 * Use {@link com.intellij.facet.ui.MultipleFacetEditorHelper} to bind controls in editor to corresponding controls in facet editors. 
 *
 * @author nik
 */
public abstract class MultipleFacetSettingsEditor {

  public abstract JComponent createComponent();

  public void disposeUIResources() {
  }

}
