/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
abstract class ProfilesConfigurableComboBox extends JPanel {
  private static final String COMBO_CARD = "combo.card";
  private static final String EDIT_CARD = "edit.card";
  private static final String PROJECT_LEVEL_SEPARATOR_TEXT = "Project Level";
  private static final String GLOBAL_LEVEL_SEPARATOR_TEXT = "Global Level";

  private final JComboBox myProfilesComboBox;
  private final CardLayout myCardLayout;
  private final ValidatedTextField mySubmitNameComponent;
  private final SaveInputComponentValidator.Wrapper mySaveListener;
  private final JPanel myComboBoxPanel;

  private InspectionProfileImpl myFirstGlobalProfile;
  private SortedComboBoxModel<InspectionProfileImpl> myComboModel;

  public ProfilesConfigurableComboBox(final ListCellRendererWrapper<InspectionProfileImpl> comboBoxItemsRenderer) {
    myComboBoxPanel = new JPanel();

    myCardLayout = new CardLayout();
    myComboBoxPanel.setLayout(myCardLayout);
    setBorder(IdeBorderFactory.createEmptyBorder(new Insets(4, 0, 6, 0)));

    myProfilesComboBox = new JComboBox();
    myComboBoxPanel.add(myProfilesComboBox, COMBO_CARD);

    mySaveListener = new SaveInputComponentValidator.Wrapper();
    mySubmitNameComponent = new ValidatedTextField(mySaveListener);
    myComboBoxPanel.add(mySubmitNameComponent, EDIT_CARD);

    myComboModel = new SortedComboBoxModel<InspectionProfileImpl>(new Comparator<InspectionProfileImpl>() {
      @Override
      public int compare(final InspectionProfileImpl p1, final InspectionProfileImpl p2) {
        return ProfilesConfigurableComboBox.this.compare(p1, p2);
      }
    });
    myProfilesComboBox.setModel(myComboModel);
    //noinspection GtkPreferredJComboBoxRenderer
    myProfilesComboBox.setRenderer(new ListCellRenderer<Object>() {

      @Override
      public Component getListCellRendererComponent(JList list,
                                                    Object o,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        InspectionProfileImpl value = (InspectionProfileImpl) o;
        TitledSeparator separator = null;
        if (index != -1) {
          if (!value.isProjectLevel()) {
            if (value == myFirstGlobalProfile) {
              separator = new TitledSeparator(GLOBAL_LEVEL_SEPARATOR_TEXT);
            }
          } else {
            if (value == myComboModel.get(0)) {
              separator = new TitledSeparator(PROJECT_LEVEL_SEPARATOR_TEXT);
            }
          }
        }
        Component renderedComponent = comboBoxItemsRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (separator == null) {
          return renderedComponent;
        }
        UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, separator.getLabel());
        separator.getLabel().setIcon(separator.getText().equals(PROJECT_LEVEL_SEPARATOR_TEXT) ? AllIcons.General.ProjectSettings : AllIcons.General.Settings);
        separator.setBorder(IdeBorderFactory.createEmptyBorder(2, 2, 0, 2));
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.add(separator, BorderLayout.NORTH);
        p.add(renderedComponent, BorderLayout.CENTER);
        return p;
      }
    });
    myProfilesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl profile = getSelectedProfile();
        if (profile != null) {
          onProfileChosen(profile);
        }
      }
    });

    setLayout(new BorderLayout());
    add(mySubmitNameComponent.getHintLabel(), BorderLayout.NORTH);
    add(myComboBoxPanel, BorderLayout.CENTER);

    showComboBoxCard();
  }

  protected abstract void onProfileChosen(final InspectionProfileImpl inspectionProfile);

  protected abstract int compare(final InspectionProfileImpl p1, final InspectionProfileImpl p2);

  protected abstract boolean isProjectLevel(final InspectionProfileImpl p1);

  void showEditCard(final String initialValue, final SaveInputComponentValidator inputValidator) {
    mySaveListener.setDelegate(inputValidator);
    mySubmitNameComponent.setText(initialValue);
    myCardLayout.show(myComboBoxPanel, EDIT_CARD);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        mySubmitNameComponent.requestFocus();
      }
    });
  }

  void reset(final Collection<InspectionProfileImpl> profiles) {
    myComboModel.clear();
    for (InspectionProfileImpl profile : profiles) {
      myComboModel.add(profile);
    }
    myProfilesComboBox.setSelectedIndex(0);
    findFirstGlobalProfile();
  }

  void addProfile(InspectionProfileImpl inspectionProfile) {
    myComboModel.add(inspectionProfile);
    if (!isProjectLevel(inspectionProfile)) {
      findFirstGlobalProfile();
    }
  }

  private void findFirstGlobalProfile() {
    myFirstGlobalProfile = null;
    for (InspectionProfileImpl profile : getProfiles()) {
      if (!isProjectLevel(profile)) {
        myFirstGlobalProfile = profile;
        break;
      }
    }
  }

  void removeProfile(InspectionProfileImpl profile) {
    myComboModel.remove(profile);
    if (!isProjectLevel(profile) && profile == myFirstGlobalProfile) {
      findFirstGlobalProfile();
    }
  }

  List<InspectionProfileImpl> getProfiles() {
    return myComboModel.getItems();
  }

  InspectionProfileImpl getSelectedProfile() {
    return (InspectionProfileImpl)myProfilesComboBox.getSelectedItem();
  }

  void selectProfile(InspectionProfileImpl inspectionProfile) {
    myProfilesComboBox.setSelectedItem(inspectionProfile);
  }

  void showComboBoxCard() {
    myCardLayout.show(myComboBoxPanel, COMBO_CARD);
  }
}
