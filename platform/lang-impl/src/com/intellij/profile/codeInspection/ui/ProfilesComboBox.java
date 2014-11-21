/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.ProjectProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Set;

/**
 * User: anna
 * Date: 30-May-2006
 */
public class ProfilesComboBox extends JComboBox {

  public static final String USE_GLOBAL_PROFILE = InspectionsBundle.message("profile.project.settings.disable.text");
  private boolean myFrozenProfilesCombo;

  private Condition<ModifiableModel> myUpdateCallback;


  public void setUpdateCallback(final Condition<ModifiableModel> updateCallback) {
    myUpdateCallback = updateCallback;
  }

  public void createProfilesCombo(final Profile selectedProfile, final Set<Profile> availableProfiles, final ProfileManager profileManager) {
    reloadProfiles(profileManager, availableProfiles, selectedProfile);

    setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Profile) {
          final Profile profile = (Profile)value;
          setText(profile.getName());
          setIcon(profile.isProjectLevel() ? AllIcons.General.ProjectSettings : AllIcons.General.Settings);
        }
        else if (value instanceof String) {
          setText((String)value);
        }
        return rendererComponent;
      }
    });
    addItemListener(new ItemListener() {
      private Object myDeselectedItem = null;

      @Override
      public void itemStateChanged(@NotNull ItemEvent e) {
        if (myFrozenProfilesCombo) {
          // do not update during reloading
          return;
        }
        else if (ItemEvent.SELECTED == e.getStateChange()) {
          final Object item = e.getItem();
          if (profileManager instanceof ProjectProfileManager && item instanceof Profile && !((Profile)item).isProjectLevel()) {
            if (Messages.showOkCancelDialog(InspectionsBundle.message("inspection.new.profile.ide.to.project.warning.message"),
                                            InspectionsBundle.message("inspection.new.profile.ide.to.project.warning.title"),
                                            Messages.getErrorIcon()) == Messages.OK) {
              final String newName = Messages.showInputDialog(InspectionsBundle.message("inspection.new.profile.text"),
                                                              InspectionsBundle.message("inspection.new.profile.dialog.title"),
                                                              Messages.getInformationIcon());
              final Object selectedItem = getSelectedItem();
              if (!StringUtil.isEmpty(newName) && selectedItem instanceof Profile) {
                if (ArrayUtil.find(profileManager.getAvailableProfileNames(), newName) == -1 &&
                    ArrayUtil.find(InspectionProfileManager.getInstance().getAvailableProfileNames(), newName) == -1) {
                  saveNewProjectProfile(newName, (Profile)selectedItem, profileManager);
                  return;
                }
                else {
                  Messages.showErrorDialog(InspectionsBundle.message("inspection.unable.to.create.profile.message", newName),
                                           InspectionsBundle.message("inspection.unable.to.create.profile.dialog.title"));
                }
              }
            }
            setSelectedItem(myDeselectedItem);
          }
        }
        else {
          myDeselectedItem = e.getItem();
        }
      }
    });
  }

  private void saveNewProjectProfile(final String newName, final Profile profile, ProfileManager profileManager) {
    InspectionProfileImpl inspectionProfile = new InspectionProfileImpl(newName, InspectionToolRegistrar.getInstance(), profileManager);
    final ModifiableModel profileModifiableModel = inspectionProfile.getModifiableModel();
    profileModifiableModel.copyFrom(profile);
    profileModifiableModel.setProjectLevel(true);
    profileModifiableModel.setName(newName);
    ((DefaultComboBoxModel)getModel()).addElement(profileModifiableModel);
    setSelectedItem(profileModifiableModel);
    if (myUpdateCallback != null){
      myUpdateCallback.value(profileModifiableModel);
    }
  }


  public void reloadProfiles(final ProfileManager profileManager, final Set<Profile> availableProfiles, final Profile selectedProfile) {
    reloadProfiles(profileManager, true, availableProfiles, selectedProfile);
  }


  public void reloadProfiles(final ProfileManager profileManager, final boolean noneItemAppearance, final Set<Profile> availableProfiles, final Profile selectedProfile) {
    myFrozenProfilesCombo = true;
    Object oldSelection = getSelectedItem();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)getModel();
    model.removeAllElements();
    if (noneItemAppearance && profileManager instanceof ProjectProfileManager) {
      model.addElement(USE_GLOBAL_PROFILE);
    }
    for (Profile profile : availableProfiles) {
      model.addElement(profile);
    }
    if (selectedProfile != null && ((!selectedProfile.isProjectLevel() && profileManager instanceof ApplicationProfileManager) ||
                                    (selectedProfile.isProjectLevel() && profileManager instanceof ProjectProfileManager))) {
      setSelectedItem(selectedProfile);
    }
    else {
      final int index = model.getIndexOf(oldSelection);
      if (index != -1) {
        setSelectedIndex(index);
      }
      else if (model.getSize() > 0) {
        setSelectedIndex(0);
      }
    }
    myFrozenProfilesCombo = false;
  }
}
