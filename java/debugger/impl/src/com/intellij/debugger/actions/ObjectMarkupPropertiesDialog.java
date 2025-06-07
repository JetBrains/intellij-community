// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkerPresentationDialogBase;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class ObjectMarkupPropertiesDialog extends ValueMarkerPresentationDialogBase {
  private static final @NonNls String MARK_ALL_REFERENCED_VALUES_KEY = "debugger.mark.all.referenced.values";
  private static final boolean MARK_ALL_REFERENCED_VALUES_DEFAULT_VALUE = true;
  private JCheckBox myCbMarkAdditionalFields;
  private final boolean mySuggestAdditionalMarkup;
  private JPanel myAdditionalPropertiesPanel;
  private MultiLineLabel myDescriptionLabel;

  public ObjectMarkupPropertiesDialog(@Nullable Component parent,
                                      final @NotNull String defaultText,
                                      boolean suggestAdditionalMarkup,
                                      @NotNull Collection<ValueMarkup> markups) {
    super(parent, defaultText, markups);
    mySuggestAdditionalMarkup = suggestAdditionalMarkup;
    myDescriptionLabel.setText(JavaDebuggerBundle.message("if.the.value.is.referenced.by.a.constant.field"));
    myCbMarkAdditionalFields.setSelected(PropertiesComponent.getInstance().getBoolean(MARK_ALL_REFERENCED_VALUES_KEY, MARK_ALL_REFERENCED_VALUES_DEFAULT_VALUE));
    init();
  }

  @Override
  protected void doOKAction() {
    if (mySuggestAdditionalMarkup) {
      PropertiesComponent.getInstance().setValue(MARK_ALL_REFERENCED_VALUES_KEY, myCbMarkAdditionalFields.isSelected(), MARK_ALL_REFERENCED_VALUES_DEFAULT_VALUE);
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent mainPanel = super.createCenterPanel();
    if (!mySuggestAdditionalMarkup) {
      return mainPanel;
    }
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, mainPanel);
    panel.add(BorderLayout.SOUTH, myAdditionalPropertiesPanel);
    return panel;
  }

  public boolean isMarkAdditionalFields() {
    return mySuggestAdditionalMarkup && myCbMarkAdditionalFields.isSelected();
  }
}
