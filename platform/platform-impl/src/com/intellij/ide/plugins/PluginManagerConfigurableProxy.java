// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.ui.UINumericRange;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableProxy
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {
  private final SearchableConfigurable myConfigurable;

  public PluginManagerConfigurableProxy() {
    if (Registry.is("show.new.plugin.page", false)) {
      myConfigurable = new PluginManagerConfigurableNew();
    }
    else {
      myConfigurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
    }
  }

  @NotNull
  @Override
  public String getId() {
    return myConfigurable.getId();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return myConfigurable.enableSearch(option);
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return myConfigurable.getDisplayName();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return myConfigurable.getHelpTopic();
  }

  @Override
  public boolean isModified(@NotNull JTextField textField, @NotNull String value) {
    return myConfigurable.isModified(textField, value);
  }

  @Override
  public boolean isModified(@NotNull JTextField textField, int value, @NotNull UINumericRange range) {
    return myConfigurable.isModified(textField, value, range);
  }

  @Override
  public boolean isModified(@NotNull JToggleButton toggleButton, boolean value) {
    return myConfigurable.isModified(toggleButton, value);
  }

  @Override
  public <T> boolean isModified(@NotNull ComboBox<T> comboBox, T value) {
    return myConfigurable.isModified(comboBox, value);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myConfigurable.getPreferredFocusedComponent();
  }

  @Override
  public boolean isAvailable() {
    return myConfigurable instanceof Configurable.TopComponentProvider;
  }

  @NotNull
  @Override
  public Component getCenterComponent(@NotNull TopComponentController controller) {
    return ((Configurable.TopComponentProvider)myConfigurable).getCenterComponent(controller);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JComponent component = myConfigurable.createComponent();
    if (component != null && myConfigurable instanceof PluginManagerConfigurable) {
      if (!component.getClass().equals(JPanel.class)) {
        // some custom components do not support borders
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(BorderLayout.CENTER, component);
        component = panel;
      }
      component.setBorder(JBUI.Borders.empty(5, 10, 10, 10));
    }
    return component;
  }

  @Override
  public boolean isModified() {
    return myConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }
}