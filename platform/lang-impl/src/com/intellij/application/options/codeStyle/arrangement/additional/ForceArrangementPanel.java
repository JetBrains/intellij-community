// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.arrangement.additional;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.OptionGroup;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;

public class ForceArrangementPanel {

  @NotNull private final JComboBox myForceRearrangeComboBox;
  @NotNull private final JPanel myPanel;

  public ForceArrangementPanel() {
    myForceRearrangeComboBox = new JComboBox();
    myForceRearrangeComboBox.setModel(new EnumComboBoxModel<>(SelectedMode.class));
    myForceRearrangeComboBox.setMaximumSize(myForceRearrangeComboBox.getPreferredSize());
    myPanel = createPanel();
  }

  public int getRearrangeMode() {
    return getSelectedMode().rearrangeMode;
  }

  public void setSelectedMode(@NotNull SelectedMode mode) {
    myForceRearrangeComboBox.setSelectedItem(mode);
  }

  public void setSelectedMode(int mode) {
    SelectedMode toSetUp = SelectedMode.getByMode(mode);
    assert toSetUp != null;
    setSelectedMode(toSetUp);
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  private JPanel createPanel() {
    OptionGroup group = new OptionGroup(ApplicationBundle.message("arrangement.settings.additional.title"));
    JPanel textWithComboPanel = new JPanel();
    textWithComboPanel.setLayout(new BoxLayout(textWithComboPanel, BoxLayout.LINE_AXIS));
    textWithComboPanel.add(new JLabel(ApplicationBundle.message("arrangement.settings.additional.force.combobox.name")));
    textWithComboPanel.add(Box.createRigidArea(JBUI.size(5, 0)));
    textWithComboPanel.add(myForceRearrangeComboBox);
    group.add(textWithComboPanel);
    return group.createPanel();
  }

  @NotNull
  private SelectedMode getSelectedMode() {
    return (SelectedMode)myForceRearrangeComboBox.getSelectedItem();
  }

  private enum SelectedMode {
    FROM_DIALOG("arrangement.settings.additional.force.rearrange.according.to.dialog", CommonCodeStyleSettings.REARRANGE_ACCORDIND_TO_DIALOG),
    ALWAYS("arrangement.settings.additional.force.rearrange.always", CommonCodeStyleSettings.REARRANGE_ALWAYS),
    NEVER("arrangement.settings.additional.force.rearrange.never", CommonCodeStyleSettings.REARRANGE_NEVER);

    public final int rearrangeMode;
    private final String myKey;

    SelectedMode(@PropertyKey(resourceBundle = ApplicationBundle.BUNDLE) String key, int mode) {
      myKey = key;
      rearrangeMode = mode;
    }

    @Nullable
    private static SelectedMode getByMode(int mode) {
      for (SelectedMode currentMode: values()) {
        if (currentMode.rearrangeMode == mode) return currentMode;
      }
      return null;
    }

    @Override
    @NotNull
    public String toString() {
      return ApplicationBundle.message(myKey);
    }
  }
}
