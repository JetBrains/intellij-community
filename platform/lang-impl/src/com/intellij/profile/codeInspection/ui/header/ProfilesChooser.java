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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public abstract class ProfilesChooser extends JPanel {
  private static final String COMBO_CARD = "combo.card";
  private static final String EDIT_CARD = "edit.card";

  private final ProfilesComboBox myProfilesComboBox;
  private final CardLayout myCardLayout;
  private final ValidatedTextField mySubmitNameComponent;
  private final SaveInputComponentValidator.Wrapper mySaveListener;
  private final JPanel myComboBoxPanel;
  private final Project myProject;

  public ProfilesChooser(Project project) {
    myProject = project;
    myComboBoxPanel = new JPanel();

    myCardLayout = new CardLayout();
    myComboBoxPanel.setLayout(myCardLayout);
    setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insets(4, 0, 6, 0)));
    myProfilesComboBox = new ProfilesComboBox() {
      @Override
      protected void onProfileChosen(InspectionProfileImpl inspectionProfile) {
        ProfilesChooser.this.onProfileChosen(inspectionProfile);
      }
    };
    myComboBoxPanel.add(myProfilesComboBox, COMBO_CARD);

    mySaveListener = new SaveInputComponentValidator.Wrapper();
    mySubmitNameComponent = new ValidatedTextField(mySaveListener);
    myComboBoxPanel.add(mySubmitNameComponent, EDIT_CARD);

    //noinspection GtkPreferredJComboBoxRenderer
    setLayout(new BorderLayout());
    add(mySubmitNameComponent.getHintLabel(), BorderLayout.NORTH);
    add(myComboBoxPanel, BorderLayout.CENTER);

    showComboBoxCard();
  }

  ProfilesComboBox getProfilesComboBox() {
    return myProfilesComboBox;
  }

  protected abstract void onProfileChosen(InspectionProfileImpl profile);

  void showEditCard(final String initialValue, final SaveInputComponentValidator inputValidator) {
    mySaveListener.setDelegate(inputValidator);
    mySubmitNameComponent.setText(initialValue);
    myCardLayout.show(myComboBoxPanel, EDIT_CARD);
    ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.getInstance(myProject).requestFocus(mySubmitNameComponent, true));
  }

  void showComboBoxCard() {
    myCardLayout.show(myComboBoxPanel, COMBO_CARD);
  }
}
