package com.intellij.facet.ui;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class MultipleFacetSettingsEditor {

  public abstract JComponent createComponent();

  public void disposeUIResources() {
  }

}
