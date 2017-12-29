/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.actions;

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
  @NonNls private static final String MARK_ALL_REFERENCED_VALUES_KEY = "debugger.mark.all.referenced.values";
  private static final boolean MARK_ALL_REFERENCED_VALUES_DEFAULT_VALUE = true;
  private JCheckBox myCbMarkAdditionalFields;
  private final boolean mySuggestAdditionalMarkup;
  private JPanel myAdditionalPropertiesPanel;
  private MultiLineLabel myDescriptionLabel;

  public ObjectMarkupPropertiesDialog(@Nullable Component parent,
                                      @NotNull final String defaultText,
                                      boolean suggestAdditionalMarkup,
                                      @NotNull Collection<ValueMarkup> markups) {
    super(parent, defaultText, markups);
    mySuggestAdditionalMarkup = suggestAdditionalMarkup;
    myDescriptionLabel.setText("If the value is referenced by a constant field of an abstract class,\n" +
                               "IDEA could additionally mark all values referenced from this class with the names of referencing fields.");
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
