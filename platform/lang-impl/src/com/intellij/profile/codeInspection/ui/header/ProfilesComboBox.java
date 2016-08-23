/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.Profile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public abstract class ProfilesComboBox extends ComboBox<InspectionProfileImpl> {
  private static final String PROJECT_LEVEL_SEPARATOR_TEXT = "Project Level";
  private static final String GLOBAL_LEVEL_SEPARATOR_TEXT = "Global Level";

  private SortedComboBoxModel<InspectionProfileImpl> myComboModel;
  private InspectionProfileImpl myFirstGlobalProfile;

  public ProfilesComboBox() {
    myComboModel = new SortedComboBoxModel<>(this::compare);
    setModel(myComboModel);
    //noinspection GtkPreferredJComboBoxRenderer
    setRenderer(new ListCellRenderer<Object>() {
      ListCellRendererWrapper<InspectionProfileImpl> baseRenderer = new ListCellRendererWrapper<InspectionProfileImpl>() {
        @Override
        public void customize(final JList list,
                              final InspectionProfileImpl value,
                              final int index,
                              final boolean selected,
                              final boolean hasFocus) {
          if (index == -1) {
            setIcon(isProjectLevel(value) ? AllIcons.General.ProjectSettings : AllIcons.General.Settings);
          }
          setText(getProfileName(value));
        }
      };

      @Override
      public Component getListCellRendererComponent(JList list,
                                                    Object o,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        InspectionProfileImpl value = (InspectionProfileImpl)o;
        TitledSeparator separator = null;
        if (index != -1) {
          if (!value.isProjectLevel()) {
            if (value == myFirstGlobalProfile) {
              separator = new TitledSeparator(GLOBAL_LEVEL_SEPARATOR_TEXT);
            }
          }
          else {
            if (value == myComboModel.get(0)) {
              separator = new TitledSeparator(PROJECT_LEVEL_SEPARATOR_TEXT);
            }
          }
        }
        Component renderedComponent = baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (separator == null) {
          return renderedComponent;
        }
        UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, separator.getLabel());
        separator.getLabel().setIcon(
          separator.getText().equals(PROJECT_LEVEL_SEPARATOR_TEXT) ? AllIcons.General.ProjectSettings : AllIcons.General.Settings);
        separator.setBackground(renderedComponent.getBackground());
        separator.setBorder(IdeBorderFactory.createEmptyBorder(2, 2, 0, 2));
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.add(separator, BorderLayout.NORTH);
        p.add(renderedComponent, BorderLayout.CENTER);
        p.setBackground(renderedComponent.getBackground());
        return p;
      }
    });
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl profile = getSelectedProfile();
        if (profile != null) {
          onProfileChosen(profile);
        }
      }
    });
  }

  protected abstract boolean isProjectLevel(final InspectionProfileImpl p);

  @NotNull
  protected abstract String getProfileName(final InspectionProfileImpl p);

  protected abstract void onProfileChosen(final InspectionProfileImpl inspectionProfile);

  public void selectProfile(InspectionProfileImpl inspectionProfile) {
    setSelectedItem(inspectionProfile);
  }

  public void reset(final Collection<? extends Profile> profiles) {
    myComboModel.clear();
    for (Profile profile : profiles) {
      myComboModel.add((InspectionProfileImpl)profile);
    }
    findFirstGlobalProfile();
    setSelectedIndex(0);
  }

  void removeProfile(InspectionProfileImpl profile) {
    myComboModel.remove(profile);
    if (!isProjectLevel(profile) && profile == myFirstGlobalProfile) {
      findFirstGlobalProfile();
    }
  }

  public void addProfile(InspectionProfileImpl inspectionProfile) {
    myComboModel.add(inspectionProfile);
    if (!isProjectLevel(inspectionProfile)) {
      findFirstGlobalProfile();
    }
  }

  InspectionProfileImpl getSelectedProfile() {
    return myComboModel.getSelectedItem();
  }

  @NotNull
  List<InspectionProfileImpl> getProfiles() {
    return myComboModel.getItems();
  }

  private int compare(@NotNull InspectionProfileImpl p1, @NotNull InspectionProfileImpl p2) {
    final boolean isProjectLevel1 = isProjectLevel(p1);
    final boolean isProjectLevel2 = isProjectLevel(p2);
    int res = Comparing.compare(isProjectLevel2, isProjectLevel1);
    if (res != 0) {
      return res;
    }
    final String currentProfileName1 = getProfileName(p1);
    final String currentProfileName2 = getProfileName(p2);
    return Comparing.compare(currentProfileName1, currentProfileName2);
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

  public void resort() {
    myComboModel.setAll(myComboModel.getItems());
    findFirstGlobalProfile();
  }
}
