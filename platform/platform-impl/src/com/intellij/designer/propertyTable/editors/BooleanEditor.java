// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ApiStatus.Internal
public final class BooleanEditor extends PropertyEditor {
  private final JCheckBox myCheckBox;
  private boolean myInsideChange;

  public BooleanEditor() {
    myCheckBox = new JCheckBox();
    myCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (!myInsideChange) {
          fireValueCommitted(false, true);
        }
      }
    });
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCheckBox);
  }

  @Override
  public Object getValue() throws Exception {
    return Boolean.valueOf(myCheckBox.isSelected());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCheckBox;
  }

  @Override
  public @NotNull JComponent getComponent(@Nullable PropertiesContainer container,
                                          @Nullable PropertyContext context, Object value,
                                          @Nullable InplaceContext inplaceContext) {
    try {
      myInsideChange = true;
      myCheckBox.setBackground(UIUtil.getTableBackground());
      myCheckBox.setSelected(value != null && (Boolean)value);
      return myCheckBox;
    }
    finally {
      myInsideChange = false;
    }
  }
}