// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.configurationStore.SerializableScheme;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class provides 'smart' isModified() behavior: it compares original settings with current snapshot by their XML 'externalized' presentations
 */
abstract class BaseRCSettingsConfigurable extends SettingsEditorConfigurable<RunnerAndConfigurationSettings> {
  BaseRCSettingsConfigurable(@NotNull SettingsEditor<RunnerAndConfigurationSettings> editor,
                             @NotNull RunnerAndConfigurationSettings settings) {
    super(editor, settings);
  }

  @Override
  public boolean isModified() {
    try {
      RunnerAndConfigurationSettings original = getSettings();

      final RunManagerImpl runManager = ((RunnerAndConfigurationSettingsImpl)original).getManager();
      if (!original.isTemplate() && !runManager.hasSettings(original)) {
        return true;
      }

      if (isSpecificallyModified()) {
        return true;
      }
      SettingsEditor<RunnerAndConfigurationSettings> editor = getEditor();
      if (editor instanceof ConfigurationSettingsEditorWrapper && !((ConfigurationSettingsEditorWrapper)editor).supportsSnapshots()) {
        return super.isModified();
      }
      RunnerAndConfigurationSettings snapshot = getSnapshot();
      return !JDOMUtil.areElementsEqual(((SerializableScheme)original).writeScheme(), ((SerializableScheme)snapshot).writeScheme());
    }
    catch (ConfigurationException e) {
      //ignore
    }
    return super.isModified();
  }

  @NotNull
  protected RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    return getEditor().getSnapshot();
  }

  boolean isSpecificallyModified() {
    return false;
  }

  @Override
  public JComponent createComponent() {
    return wrapWithScrollPane(super.createComponent());
  }

  @NotNull
  protected static JBScrollPane wrapWithScrollPane(@Nullable JComponent component) {
    JBScrollPane scrollPane =
      new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
        @Override
        public Dimension getMinimumSize() {
          Dimension d = super.getMinimumSize();
          JViewport viewport = getViewport();
          if (viewport != null) {
            Component view = viewport.getView();
            if (view instanceof Scrollable) {
              d.width = ((Scrollable)view).getPreferredScrollableViewportSize().width;
            }
            if (view != null) {
              d.width = view.getMinimumSize().width;
            }
          }
          d.height = Math.max(d.height, JBUIScale.scale(400));
          return d;
        }
      };
    scrollPane.setBorder(JBUI.Borders.empty());
    scrollPane.setViewportBorder(JBUI.Borders.empty());
    if (component != null) {
      scrollPane.getViewport().setView(component);
    }
    return scrollPane;
  }
}
