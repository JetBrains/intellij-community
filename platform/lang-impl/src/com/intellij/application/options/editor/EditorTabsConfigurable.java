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

package com.intellij.application.options.editor;

import com.intellij.ide.ui.UINumericRange;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class EditorTabsConfigurable implements EditorOptionsProvider {
  private static final UINumericRange EDITOR_TABS_RANGE = new UINumericRange(10, 1, 100);
  private JPanel myRootPanel;
  private JCheckBox myHideKnownExtensions;
  private JCheckBox myScrollTabLayoutInEditorCheckBox;
  private JTextField myEditorTabLimitField;
  private JComboBox myEditorTabPlacement;
  private JRadioButton myCloseNonModifiedFilesFirstRadio;
  private JRadioButton myCloseLRUFilesRadio;
  private JRadioButton myActivateLeftEditorOnCloseRadio;
  private JRadioButton myActivateMRUEditorOnCloseRadio;
  private JCheckBox myCbModifiedTabsMarkedWithAsterisk;
  private JCheckBox myShowTabsTooltipsCheckBox;
  private JCheckBox myShowCloseButtonOnCheckBox;
  private JCheckBox myShowDirectoryInTabCheckBox;
  private JRadioButton myActivateRightNeighbouringTabRadioButton;
  private JCheckBox myHideTabsCheckbox;
  private JCheckBox myReuseNotModifiedTabsCheckBox;

  public EditorTabsConfigurable() {
    myEditorTabPlacement.setModel(new DefaultComboBoxModel(new Object[]{
      SwingConstants.TOP,
      SwingConstants.LEFT,
      SwingConstants.BOTTOM,
      SwingConstants.RIGHT,
      UISettings.TABS_NONE,
    }));
    myEditorTabPlacement.setRenderer(new MyTabsPlacementComboBoxRenderer());
    myEditorTabPlacement.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        revalidateSingleRowCheckbox();
      }
    });

    revalidateSingleRowCheckbox();
    myScrollTabLayoutInEditorCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
        myHideTabsCheckbox.setEnabled(myScrollTabLayoutInEditorCheckBox.isSelected());
      }
    });
  }

  private void revalidateSingleRowCheckbox() {
    final int i = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();

    boolean none = i == UISettings.TABS_NONE;
    myHideKnownExtensions.setEnabled(!none);
    myHideTabsCheckbox.setEnabled(!none && myScrollTabLayoutInEditorCheckBox.isSelected());
    myScrollTabLayoutInEditorCheckBox.setEnabled(!none);
    myCbModifiedTabsMarkedWithAsterisk.setEnabled(!none);
    myShowTabsTooltipsCheckBox.setEnabled(!none);
    myShowCloseButtonOnCheckBox.setEnabled(!none);
    myShowDirectoryInTabCheckBox.setEnabled(!none);

    if (SwingConstants.TOP == i) {
      myScrollTabLayoutInEditorCheckBox.setEnabled(true);
    } else {
      myScrollTabLayoutInEditorCheckBox.setSelected(true);
      myScrollTabLayoutInEditorCheckBox.setEnabled(false);
    }
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Editor Tabs";
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.tabs";
  }

  @Override
  public JComponent createComponent() {
    return myRootPanel;
  }

  @Override
  public void reset() {
    UISettings uiSettings=UISettings.getInstance();

    myCbModifiedTabsMarkedWithAsterisk.setSelected(uiSettings.getMarkModifiedTabsWithAsterisk());
    myShowTabsTooltipsCheckBox.setSelected(uiSettings.getShowTabsTooltips());
    myScrollTabLayoutInEditorCheckBox.setSelected(uiSettings.getScrollTabLayoutInEditor());
    myHideTabsCheckbox.setEnabled(myScrollTabLayoutInEditorCheckBox.isSelected());
    myHideTabsCheckbox.setSelected(uiSettings.getHideTabsIfNeed());
    myEditorTabPlacement.setSelectedItem(uiSettings.getEditorTabPlacement());
    myHideKnownExtensions.setSelected(uiSettings.getHideKnownExtensionInTabs());
    myShowDirectoryInTabCheckBox.setSelected(uiSettings.getShowDirectoryForNonUniqueFilenames());
    myEditorTabLimitField.setText(Integer.toString(uiSettings.getEditorTabLimit()));
    myReuseNotModifiedTabsCheckBox.setSelected(uiSettings.getReuseNotModifiedTabs());
    myShowCloseButtonOnCheckBox.setSelected(uiSettings.getShowCloseButton());

    if (uiSettings.getCloseNonModifiedFilesFirst()) {
      myCloseNonModifiedFilesFirstRadio.setSelected(true);
    }
    else {
      myCloseLRUFilesRadio.setSelected(true);
    }
    if (uiSettings.getActiveMruEditorOnClose()) {
      myActivateMRUEditorOnCloseRadio.setSelected(true);
    }
    else if (uiSettings.getActiveRightEditorOnClose()) {
      myActivateRightNeighbouringTabRadioButton.setSelected(true);
    }
    else {
      myActivateLeftEditorOnCloseRadio.setSelected(true);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    UISettings uiSettings=UISettings.getInstance();

    boolean uiSettingsChanged = uiSettings.getMarkModifiedTabsWithAsterisk() != myCbModifiedTabsMarkedWithAsterisk.isSelected();
    uiSettings.setMarkModifiedTabsWithAsterisk(myCbModifiedTabsMarkedWithAsterisk.isSelected());

    if (isModified(myShowTabsTooltipsCheckBox, uiSettings.getShowTabsTooltips())) uiSettingsChanged = true;
    uiSettings.setShowTabsTooltips(myShowTabsTooltipsCheckBox.isSelected());

    if (isModified(myScrollTabLayoutInEditorCheckBox, uiSettings.getScrollTabLayoutInEditor())) uiSettingsChanged = true;
    uiSettings.setScrollTabLayoutInEditor(myScrollTabLayoutInEditorCheckBox.isSelected());

    if (isModified(myHideTabsCheckbox, uiSettings.getHideTabsIfNeed())) uiSettingsChanged = true;
    uiSettings.setHideTabsIfNeed(myHideTabsCheckbox.isSelected());

    if (isModified(myShowCloseButtonOnCheckBox, uiSettings.getShowCloseButton())) uiSettingsChanged = true;
    uiSettings.setShowCloseButton(myShowCloseButtonOnCheckBox.isSelected());

    final int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    if (uiSettings.getEditorTabPlacement() != tabPlacement) uiSettingsChanged = true;
    uiSettings.setEditorTabPlacement(tabPlacement);

    boolean hide = myHideKnownExtensions.isSelected();
    if (uiSettings.getHideKnownExtensionInTabs() != hide) uiSettingsChanged = true;
    uiSettings.setHideKnownExtensionInTabs(hide);

    boolean dir = myShowDirectoryInTabCheckBox.isSelected();
    if (uiSettings.getShowDirectoryForNonUniqueFilenames() != dir) uiSettingsChanged = true;
    uiSettings.setShowDirectoryForNonUniqueFilenames(dir);

    uiSettings.setCloseNonModifiedFilesFirst(myCloseNonModifiedFilesFirstRadio.isSelected());
    uiSettings.setActiveMruEditorOnClose(myActivateMRUEditorOnCloseRadio.isSelected());
    uiSettings.setActiveRightEditorOnClose(myActivateRightNeighbouringTabRadioButton.isSelected());

    if (isModified(myReuseNotModifiedTabsCheckBox, uiSettings.getReuseNotModifiedTabs())) uiSettingsChanged = true;
    uiSettings.setReuseNotModifiedTabs(myReuseNotModifiedTabsCheckBox.isSelected());

    if (isModified(myEditorTabLimitField, uiSettings.getEditorTabLimit(), EDITOR_TABS_RANGE)) uiSettingsChanged = true;
    try {
      uiSettings.setEditorTabLimit(EDITOR_TABS_RANGE.fit(Integer.parseInt(myEditorTabLimitField.getText().trim())));
    }
    catch (NumberFormatException ignored) {
    }
    if(uiSettingsChanged){
      uiSettings.fireUISettingsChanged();
    }
  }

  @Override
  public boolean isModified() {
    final UISettings uiSettings = UISettings.getInstance();
    boolean isModified = isModified(myCbModifiedTabsMarkedWithAsterisk, uiSettings.getMarkModifiedTabsWithAsterisk());
    isModified |= isModified(myShowTabsTooltipsCheckBox, uiSettings.getShowTabsTooltips());
    isModified |= isModified(myEditorTabLimitField, uiSettings.getEditorTabLimit());
    isModified |= isModified(myReuseNotModifiedTabsCheckBox, uiSettings.getReuseNotModifiedTabs());
    int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    isModified |= tabPlacement != uiSettings.getEditorTabPlacement();
    isModified |= myHideKnownExtensions.isSelected() != uiSettings.getHideKnownExtensionInTabs();
    isModified |= myShowDirectoryInTabCheckBox.isSelected() != uiSettings.getShowDirectoryForNonUniqueFilenames();

    isModified |= myScrollTabLayoutInEditorCheckBox.isSelected() != uiSettings.getScrollTabLayoutInEditor();
    isModified |= myHideTabsCheckbox.isSelected() != uiSettings.getHideTabsIfNeed();
    isModified |= myShowCloseButtonOnCheckBox.isSelected() != uiSettings.getShowCloseButton();

    isModified |= isModified(myCloseNonModifiedFilesFirstRadio, uiSettings.getCloseNonModifiedFilesFirst());
    isModified |= isModified(myActivateMRUEditorOnCloseRadio, uiSettings.getActiveMruEditorOnClose());
    isModified |= isModified(myActivateRightNeighbouringTabRadioButton, uiSettings.getActiveRightEditorOnClose());

    return isModified;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  private static final class MyTabsPlacementComboBoxRenderer extends ListCellRendererWrapper<Integer> {
    public MyTabsPlacementComboBoxRenderer() {
      super();
    }

    @Override
    public void customize(JList list, Integer value, int index, boolean selected, boolean hasFocus) {
      int tabPlacement = value.intValue();
      String text;
      if (UISettings.TABS_NONE == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.none");
      }
      else if (SwingConstants.TOP == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.top");
      }
      else if (SwingConstants.LEFT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.left");
      }
      else if (SwingConstants.BOTTOM == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.bottom");
      }
      else if (SwingConstants.RIGHT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.right");
      }
      else {
        throw new IllegalArgumentException("unknown tabPlacement: " + tabPlacement);
      }
      setText(text);
    }
  }

  @Override
  @NotNull
  public String getId() {
    return "editor.preferences.tabs";
  }
}
