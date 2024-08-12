// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Trinity;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public final class TemplateKindCombo extends ComboboxWithBrowseButton {
  public TemplateKindCombo() {
    getComboBox().setRenderer(
      SimpleListCellRenderer.create(
        (JBLabel label, Trinity<@NlsContexts.ListItem String, Icon, String> value, int index) -> {
        if (value != null) {
          label.setText(value.first);
          label.setIcon(value.second);
        }
      }));

    ComboboxSpeedSearch search = new ComboboxSpeedSearch(getComboBox(), null) {
      @Override
      protected String getElementText(Object element) {
        if (element instanceof Trinity) {
          return (String)((Trinity<?, ?, ?>)element).first;
        }
        return null;
      }
    };
    search.setupListeners();
    search.setComparator(new SpeedSearchComparator(true));
    setButtonListener(null);
  }

  public void addItem(@NlsContexts.ListItem @NotNull String presentableName, @Nullable Icon icon, @NotNull String templateName) {
    //noinspection unchecked
    getComboBox().addItem(new Trinity<>(presentableName, icon, templateName));
  }

  public @NotNull @NlsSafe String getSelectedName() {
    //noinspection unchecked
    final Trinity<String, Icon, String> trinity = (Trinity<String, Icon, String>)getComboBox().getSelectedItem();
    if (trinity == null) {
      // design time
      return "yet_unknown";
    }
    return trinity.third;
  }

  public void setSelectedName(@Nullable String name) {
    if (name == null) return;
    ComboBoxModel model = getComboBox().getModel();
    for (int i = 0, n = model.getSize(); i < n; i++) {
      //noinspection unchecked
      Trinity<String, Icon, String> trinity = (Trinity<String, Icon, String>)model.getElementAt(i);
      if (name.equals(trinity.third)) {
        getComboBox().setSelectedItem(trinity);
        return;
      }
    }
  }

  public void registerUpDownHint(JComponent component) {
    DumbAwareAction.create(e -> {
      if (e.getInputEvent() instanceof KeyEvent) {
        int code = ((KeyEvent)e.getInputEvent()).getKeyCode();
        scrollBy(code == KeyEvent.VK_DOWN ? 1 : code == KeyEvent.VK_UP ? -1 : 0);
      }
    }).registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), component);
  }

  private void scrollBy(int delta) {
    final int size = getComboBox().getModel().getSize();
    if (delta == 0 || size == 0) return;
    int next = getComboBox().getSelectedIndex() + delta;
    if (next < 0 || next >= size) {
      if (!UISettings.getInstance().getCycleScrolling()) {
        return;
      }
      next = (next + size) % size;
    }
    getComboBox().setSelectedIndex(next);
  }

  /**
   * @param listener pass {@code null} to hide browse button
   */
  public void setButtonListener(@Nullable ActionListener listener) {
    getButton().setVisible(listener != null);
    if (listener != null) {
      addActionListener(listener);
    }
  }

  public void clear() {
    getComboBox().removeAllItems();
  }
}
