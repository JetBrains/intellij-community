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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.ui.classFilter.ClassFilterEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class DebuggerSteppingConfigurable implements ConfigurableUi<DebuggerSettings> {
  private JCheckBox myCbStepInfoFiltersEnabled;
  private JCheckBox myCbSkipSyntheticMethods;
  private JCheckBox myCbSkipConstructors;
  private JCheckBox myCbSkipClassLoaders;
  private ClassFilterEditor mySteppingFilterEditor;
  private JCheckBox myCbSkipSimpleGetters;
  private JRadioButton myRbEvaluateFinallyAlways;
  private JRadioButton myRbEvaluateFinallyNever;
  private JRadioButton myRbEvaluateFinallyAsk;
  private JCheckBox myCbResumeOnlyCurrentThread;

  @Override
  public void reset(@NotNull DebuggerSettings settings) {
    myCbSkipSimpleGetters.setSelected(settings.SKIP_GETTERS);
    myCbSkipSyntheticMethods.setSelected(settings.SKIP_SYNTHETIC_METHODS);
    myCbSkipConstructors.setSelected(settings.SKIP_CONSTRUCTORS);
    myCbSkipClassLoaders.setSelected(settings.SKIP_CLASSLOADERS);

    myCbStepInfoFiltersEnabled.setSelected(settings.TRACING_FILTERS_ENABLED);

    mySteppingFilterEditor.setFilters(settings.getSteppingFilters());
    mySteppingFilterEditor.setEnabled(settings.TRACING_FILTERS_ENABLED);

    if (DebuggerSettings.EVALUATE_FINALLY_ALWAYS.equals(settings.EVALUATE_FINALLY_ON_POP_FRAME)) {
      myRbEvaluateFinallyAlways.setSelected(true);
    }
    else if (DebuggerSettings.EVALUATE_FINALLY_NEVER.equals(settings.EVALUATE_FINALLY_ON_POP_FRAME)) {
      myRbEvaluateFinallyNever.setSelected(true);
    }
    else {
      myRbEvaluateFinallyAsk.setSelected(true);
    }
    myCbResumeOnlyCurrentThread.setSelected(settings.RESUME_ONLY_CURRENT_THREAD);
  }

  @Override
  public void apply(@NotNull DebuggerSettings settings) {
    mySteppingFilterEditor.stopEditing();
    getSettingsTo(settings);
  }

  private void getSettingsTo(DebuggerSettings settings) {
    settings.SKIP_GETTERS = myCbSkipSimpleGetters.isSelected();
    settings.SKIP_SYNTHETIC_METHODS = myCbSkipSyntheticMethods.isSelected();
    settings.SKIP_CONSTRUCTORS = myCbSkipConstructors.isSelected();
    settings.SKIP_CLASSLOADERS = myCbSkipClassLoaders.isSelected();
    settings.TRACING_FILTERS_ENABLED = myCbStepInfoFiltersEnabled.isSelected();

    if (myRbEvaluateFinallyAlways.isSelected()) {
      settings.EVALUATE_FINALLY_ON_POP_FRAME = DebuggerSettings.EVALUATE_FINALLY_ALWAYS;
    }
    else if (myRbEvaluateFinallyNever.isSelected()) {
      settings.EVALUATE_FINALLY_ON_POP_FRAME = DebuggerSettings.EVALUATE_FINALLY_NEVER;
    }
    else {
      settings.EVALUATE_FINALLY_ON_POP_FRAME = DebuggerSettings.EVALUATE_FINALLY_ASK;
    }

    settings.RESUME_ONLY_CURRENT_THREAD = myCbResumeOnlyCurrentThread.isSelected();
    settings.setSteppingFilters(mySteppingFilterEditor.getFilters());
  }

  @Override
  public boolean isModified(@NotNull DebuggerSettings currentSettings) {
    DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myCbSkipSyntheticMethods = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.synthetic.methods"));
    myCbSkipConstructors = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.constructors"));
    myCbSkipClassLoaders = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.classLoaders"));
    myCbSkipSimpleGetters = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.simple.getters"));
    myCbStepInfoFiltersEnabled = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.step.filters.list.header"));
    panel.add(myCbSkipSyntheticMethods, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),0, 0));
    panel.add(myCbSkipConstructors, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),0, 0));
    panel.add(myCbSkipClassLoaders, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),0, 0));
    panel.add(myCbSkipSimpleGetters, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),0, 0));
    panel.add(myCbStepInfoFiltersEnabled, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0),0, 0));

    mySteppingFilterEditor = new ClassFilterEditor(JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables(), null, "reference.viewBreakpoints.classFilters.newPattern");
    panel.add(mySteppingFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 5, 0, 0),0, 0));

    myCbStepInfoFiltersEnabled.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySteppingFilterEditor.setEnabled(myCbStepInfoFiltersEnabled.isSelected());
      }
    });

    myRbEvaluateFinallyAlways = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.always"));
    myRbEvaluateFinallyNever = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.never"));
    myRbEvaluateFinallyAsk = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.ask"));

    int cbLeftOffset = 0;
    final Border border = myCbSkipSimpleGetters.getBorder();
    if (border != null) {
      final Insets insets = border.getBorderInsets(myCbSkipSimpleGetters);
      if (insets != null) {
        cbLeftOffset = insets.left;
      }
    }

    final ButtonGroup group = new ButtonGroup();
    group.add(myRbEvaluateFinallyAlways);
    group.add(myRbEvaluateFinallyNever);
    group.add(myRbEvaluateFinallyAsk);
    final Box box = Box.createHorizontalBox();
    box.add(myRbEvaluateFinallyAlways);
    box.add(myRbEvaluateFinallyNever);
    box.add(myRbEvaluateFinallyAsk);
    final JPanel evalFinallyPanel = new JPanel(new BorderLayout());
    evalFinallyPanel.add(box, BorderLayout.CENTER);
    evalFinallyPanel.add(new JLabel(DebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.on.pop")), BorderLayout.WEST);
    panel.add(evalFinallyPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(4, cbLeftOffset, 0, 0), 0, 0));

    myCbResumeOnlyCurrentThread = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.resume.only.current.thread"));
    panel.add(myCbResumeOnlyCurrentThread, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),0, 0));

    return panel;
  }
}