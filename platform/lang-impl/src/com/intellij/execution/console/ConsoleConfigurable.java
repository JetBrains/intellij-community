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
package com.intellij.execution.console;

import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AddEditDeleteListPanel;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class ConsoleConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainComponent;
  private JCheckBox myCbUseSoftWrapsAtConsole;
  private JTextField myCommandsHistoryLimitField;
  private JCheckBox myCbOverrideConsoleCycleBufferSize;
  private JTextField myConsoleCycleBufferSizeField;


  private MyAddDeleteListPanel myPositivePanel;
  private MyAddDeleteListPanel myNegativePanel;
  private final ConsoleFoldingSettings mySettings = ConsoleFoldingSettings.getSettings();

  @Override
  public JComponent createComponent() {
    if (myMainComponent == null) {
      myMainComponent = new JPanel(new BorderLayout());
      myCbUseSoftWrapsAtConsole = new JCheckBox(ApplicationBundle.message("checkbox.use.soft.wraps.at.console"), false);
      myCommandsHistoryLimitField = new JTextField(3);
      myCbOverrideConsoleCycleBufferSize = new JCheckBox(ApplicationBundle.message("checkbox.override.console.cycle.buffer.size", String.valueOf(ConsoleBuffer.getLegacyCycleBufferSize() / 1024)), false);
      myCbOverrideConsoleCycleBufferSize.addChangeListener(new ChangeListener(){
        @Override
        public void stateChanged(ChangeEvent e) {
          myConsoleCycleBufferSizeField.setEnabled(myCbOverrideConsoleCycleBufferSize.isSelected());
        }
      });
      myConsoleCycleBufferSizeField = new JTextField(3);

      JPanel northPanel = new JPanel(new GridBagLayout());
      GridBag gridBag = new GridBag();
      gridBag.anchor(GridBagConstraints.WEST).setDefaultAnchor(GridBagConstraints.WEST);
      northPanel
        .add(myCbUseSoftWrapsAtConsole,
             gridBag.nextLine().next());
      northPanel.add(Box.createHorizontalGlue(), gridBag.next().coverLine());
      northPanel.add(new JLabel(ApplicationBundle.message("editbox.console.history.limit")), gridBag.nextLine().next());
      northPanel.add(myCommandsHistoryLimitField, gridBag.next());
      if (ConsoleBuffer.useCycleBuffer()) {
        northPanel.add(myCbOverrideConsoleCycleBufferSize, gridBag.nextLine().next());
        northPanel.add(myConsoleCycleBufferSizeField, gridBag.next());
      }
      if (!editFoldingsOnly()) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(northPanel, BorderLayout.WEST);
        myMainComponent.add(wrapper, BorderLayout.NORTH);
      }
      Splitter splitter = new Splitter(true);
      myMainComponent.add(splitter, BorderLayout.CENTER);
      myPositivePanel =
        new MyAddDeleteListPanel("Fold console lines that contain", "Enter a substring of a console line you'd like to see folded:");
      myNegativePanel = new MyAddDeleteListPanel("Exceptions", "Enter a substring of a console line you don't want to fold:");
      splitter.setFirstComponent(myPositivePanel);
      splitter.setSecondComponent(myNegativePanel);

      myPositivePanel.getEmptyText().setText("Fold nothing");
      myNegativePanel.getEmptyText().setText("No exceptions");
    }
    return myMainComponent;
  }

  protected boolean editFoldingsOnly() {
    return false;
  }

  public void addRule(@NotNull String rule) {
    myPositivePanel.addRule(rule);
  }

  @Override
  public boolean isModified() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    boolean isModified = !Arrays.asList(myNegativePanel.getListItems()).equals(mySettings.getNegativePatterns());
    isModified |= !Arrays.asList(myPositivePanel.getListItems()).equals(mySettings.getPositivePatterns());
    isModified |= isModified(myCbUseSoftWrapsAtConsole, editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE));
    isModified |= isModified(myCommandsHistoryLimitField, UISettings.getInstance().CONSOLE_COMMAND_HISTORY_LIMIT);
    if (ConsoleBuffer.useCycleBuffer()) {
      isModified |= isModified(myCbOverrideConsoleCycleBufferSize, UISettings.getInstance().OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE);
      isModified |= isModified(myConsoleCycleBufferSizeField, UISettings.getInstance().CONSOLE_CYCLE_BUFFER_SIZE_KB);
    }

    return isModified;
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    UISettings uiSettings = UISettings.getInstance();

    editorSettings.setUseSoftWraps(myCbUseSoftWrapsAtConsole.isSelected(), SoftWrapAppliancePlaces.CONSOLE);
    boolean uiSettingsChanged = false;
    if (isModified(myCommandsHistoryLimitField, uiSettings.CONSOLE_COMMAND_HISTORY_LIMIT)) {
      uiSettings.CONSOLE_COMMAND_HISTORY_LIMIT = Math.max(0, Math.min(1000, Integer.parseInt(myCommandsHistoryLimitField.getText().trim())));
      uiSettingsChanged = true;
    }
    if (ConsoleBuffer.useCycleBuffer()) {
      if (isModified(myCbOverrideConsoleCycleBufferSize, uiSettings.OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE)) {
        uiSettings.OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = myCbOverrideConsoleCycleBufferSize.isSelected();
        uiSettingsChanged = true;
      }
      if (isModified(myConsoleCycleBufferSizeField, uiSettings.CONSOLE_CYCLE_BUFFER_SIZE_KB)) {
        uiSettings.CONSOLE_CYCLE_BUFFER_SIZE_KB = Math.max(0, Math.min(1024*100, Integer.parseInt(myConsoleCycleBufferSizeField.getText().trim())));
        uiSettingsChanged = true;
      }
    }
    if (uiSettingsChanged) {
      uiSettings.fireUISettingsChanged();
    }


    myNegativePanel.applyTo(mySettings.getNegativePatterns());
    myPositivePanel.applyTo(mySettings.getPositivePatterns());
  }

  @Override
  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    UISettings uiSettings = UISettings.getInstance();

    myCbUseSoftWrapsAtConsole.setSelected(editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE));
    myCommandsHistoryLimitField.setText(Integer.toString(uiSettings.CONSOLE_COMMAND_HISTORY_LIMIT));

    myCbOverrideConsoleCycleBufferSize.setEnabled(ConsoleBuffer.useCycleBuffer());
    myCbOverrideConsoleCycleBufferSize.setSelected(uiSettings.OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE);
    myConsoleCycleBufferSizeField.setEnabled(ConsoleBuffer.useCycleBuffer() && uiSettings.OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE);
    myConsoleCycleBufferSizeField.setText(Integer.toString(uiSettings.CONSOLE_CYCLE_BUFFER_SIZE_KB));


    myNegativePanel.resetFrom(mySettings.getNegativePatterns());
    myPositivePanel.resetFrom(mySettings.getPositivePatterns());
  }

  @Override
  public void disposeUIResources() {
    myMainComponent = null;
    myNegativePanel = null;
    myPositivePanel = null;
  }

  @Override
  @NotNull
  public String getId() {
    return getDisplayName();
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Console";
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.console.folding";
  }

  private static class MyAddDeleteListPanel extends AddEditDeleteListPanel<String> {
    private final String myQuery;

    public MyAddDeleteListPanel(String title, String query) {
      super(title, new ArrayList<>());
      myQuery = query;
    }

    @Override
    @Nullable
    protected String findItemToAdd() {
      return showEditDialog("");
    }

    @Nullable
    private String showEditDialog(final String initialValue) {
      return Messages.showInputDialog(this, myQuery, "Folding Pattern", Messages.getQuestionIcon(), initialValue, new InputValidatorEx() {
        @Override
        public boolean checkInput(String inputString) {
          return !StringUtil.isEmpty(inputString);
        }

        @Override
        public boolean canClose(String inputString) {
          return !StringUtil.isEmpty(inputString);
        }

        @Nullable
        @Override
        public String getErrorText(String inputString) {
          if (!checkInput(inputString)) {
            return "Console folding rule string cannot be empty";
          }
          return null;
        }
      });
    }

    void resetFrom(List<String> patterns) {
      myListModel.clear();
      for (String pattern : patterns) {
        myListModel.addElement(pattern);
      }
    }

    void applyTo(List<String> patterns) {
      patterns.clear();
      for (Object o : getListItems()) {
        patterns.add((String)o);
      }
    }

    public void addRule(String rule) {
      addElement(rule);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      super.setBounds(x, y, width, height);
    }

    @Override
    protected String editSelectedItem(String item) {
      return showEditDialog(item);
    }
  }
}
