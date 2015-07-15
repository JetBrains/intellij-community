/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class TestDiscoveryConfigurable<T extends TestDiscoveryConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  private final ConfigurationModuleSelector myModuleSelector;
  // Fields
  private JPanel myWholePanel = new JPanel(new BorderLayout());
  private LabeledComponent<JComboBox> myModule = new LabeledComponent<JComboBox>();
  private CommonJavaParametersPanel myCommonJavaParameters = new CommonJavaParametersPanel();
  private AlternativeJREPanel myAlternativeJREPanel = new AlternativeJREPanel();
  private JTextField myPosition = new JTextField();
  private ComboBox myChangeLists = new ComboBox();
  private JRadioButton myPositionRb = new JRadioButton("Tests for method:");
  private JRadioButton myChangesRb = new JRadioButton("Tests for change list:");
  private JComponent anchor;

  public TestDiscoveryConfigurable(final Project project) {
    myModule.setText("Use classpath of");
    myModule.setLabelLocation(BorderLayout.WEST);
    myModule.setComponent(new JComboBox());
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
    myCommonJavaParameters.setHasModuleMacro();
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    final JPanel panelWithSettings = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         new Insets(0, 0, 0, 0), 0, 0);
    panelWithSettings.add(myPositionRb, gc);
    panelWithSettings.add(myPosition, gc);
    panelWithSettings.add(myChangesRb, gc);
    panelWithSettings.add(myChangeLists, gc);

    ButtonGroup gr = new ButtonGroup();
    gr.add(myPositionRb);
    gr.add(myChangesRb);

    final ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateComponents();
      }
    };
    myPositionRb.addActionListener(l);
    myChangesRb.addActionListener(l);


    final java.util.List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement("All");
    for (LocalChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
    myChangeLists.setModel(model);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.getAffectedFiles().isEmpty()) {
      myChangesRb.setEnabled(false);
    }

    myWholePanel.add(panelWithSettings, BorderLayout.NORTH);
    myWholePanel.add(myCommonJavaParameters, BorderLayout.CENTER);
    final JPanel classpathPanel = new JPanel(new BorderLayout());
    myWholePanel.add(classpathPanel, BorderLayout.SOUTH);

    classpathPanel.add(myModule, BorderLayout.NORTH);
    classpathPanel.add(myAlternativeJREPanel, BorderLayout.CENTER);
    UIUtil.setEnabled(myCommonJavaParameters.getProgramParametersComponent(), false, true);

    setAnchor(myModule.getLabel());
    myAlternativeJREPanel.setAnchor(myModule.getLabel());
    myCommonJavaParameters.setAnchor(myModule.getLabel());
  }

  private void updateComponents() {
    myPosition.setEnabled(myPositionRb.isSelected());
    myChangeLists.setEnabled(myChangesRb.isSelected());
  }

  public void applyEditorTo(final TestDiscoveryConfiguration configuration) {
    applyHelpersTo(configuration);
    configuration.setAlternativeJrePath(myAlternativeJREPanel.getPath());
    configuration.setAlternativeJrePathEnabled(myAlternativeJREPanel.isPathEnabled());
    configuration.setPosition(myPositionRb.isSelected() ? myPosition.getText() : null);
    if (myChangesRb.isSelected()) {
      final Object selectedItem = myChangeLists.getSelectedItem();
      configuration.setChangeList("All".equals(selectedItem) ? null : (String)selectedItem);
    }
    else {
      configuration.setChangeList(null);
    }
    myCommonJavaParameters.applyTo(configuration);
  }

  public void resetEditorFrom(final TestDiscoveryConfiguration configuration) {
    myCommonJavaParameters.reset(configuration);
    getModuleSelector().reset(configuration);
    myAlternativeJREPanel.init(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    final String position = configuration.getPosition();
    if (position != null) {
      myPositionRb.setSelected(true);
      myPosition.setText(position);
    }
    else if (myChangesRb.isEnabled()) {
      myChangesRb.setSelected(true);
    }
    else {
      myPositionRb.setSelected(true);
    }
    final String changeList = configuration.getChangeList();
    if (changeList != null) {
      myChangeLists.setSelectedItem(changeList);
    }
    else if (myChangesRb.isEnabled()) {
      myChangeLists.setSelectedIndex(0);
    }
    updateComponents();
  }

  public JComboBox getModulesComponent() {
    return myModule.getComponent();
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
  }


  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void applyHelpersTo(final TestDiscoveryConfiguration currentState) {
    myCommonJavaParameters.applyTo(currentState);
    getModuleSelector().applyTo(currentState);
  }
}
