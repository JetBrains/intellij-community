package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class KeymapConfigurable extends SearchableConfigurable.Parent.Abstract {
  private static final Icon icon = IconLoader.getIcon("/general/keymap.png");

  public String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  public Icon getIcon() {
    return icon;
  }
  
  public String getHelpTopic() {
    return "preferences.keymap";
  }


  protected Configurable[] buildConfigurables() {
    KeymapPanel keymap = new KeymapPanel();
    QuickListsPanel quickLists = new QuickListsPanel(keymap);
    quickLists.reset();
    keymap.setQuickListsPanel(quickLists);
    return new Configurable[]{keymap, quickLists};
  }

  public String getId() {
    return "preferences.keymap.keymap";
  }

  @Override
  public boolean isVisible() {
    return false;
  }
}