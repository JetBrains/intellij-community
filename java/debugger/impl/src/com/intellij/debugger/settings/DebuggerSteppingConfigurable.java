// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.*;

class DebuggerSteppingConfigurable implements ConfigurableUi<DebuggerSettings> {
  private JCheckBox myCbAlwaysSmartStep;
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
    myCbAlwaysSmartStep.setSelected(settings.ALWAYS_SMART_STEP_INTO);

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
    settings.ALWAYS_SMART_STEP_INTO = myCbAlwaysSmartStep.isSelected();
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
    myCbAlwaysSmartStep = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.always.smart.step.into"));
    myCbSkipSyntheticMethods = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.skip.synthetic.methods"));
    myCbSkipConstructors = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.skip.constructors"));
    myCbSkipClassLoaders = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.skip.classLoaders"));
    myCbSkipSimpleGetters = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.skip.simple.getters"));
    myCbStepInfoFiltersEnabled = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.step.filters.list.header"));
    panel.add(myCbAlwaysSmartStep, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    panel.add(myCbSkipSyntheticMethods, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsTop(8), 0, 0));
    panel.add(myCbSkipConstructors, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    panel.add(myCbSkipClassLoaders, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    panel.add(myCbSkipSimpleGetters, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    panel.add(myCbStepInfoFiltersEnabled, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsTop(8), 0, 0));

    mySteppingFilterEditor = new ClassFilterEditor(JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables(), null, "reference.viewBreakpoints.classFilters.newPattern");
    panel.add(mySteppingFilterEditor, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 1.0, CENTER, BOTH, JBUI.insetsLeft(5), 0, 0));

    myCbStepInfoFiltersEnabled.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySteppingFilterEditor.setEnabled(myCbStepInfoFiltersEnabled.isSelected());
      }
    });

    myRbEvaluateFinallyAlways = new JRadioButton(JavaDebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.always"));
    myRbEvaluateFinallyNever = new JRadioButton(JavaDebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.never"));
    myRbEvaluateFinallyAsk = new JRadioButton(JavaDebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.ask"));

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
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbEvaluateFinallyAlways);
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbEvaluateFinallyNever);
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbEvaluateFinallyAsk);
    final JPanel evalFinallyPanel = new JPanel(new BorderLayout());
    evalFinallyPanel.add(box, BorderLayout.CENTER);
    evalFinallyPanel.add(new JLabel(JavaDebuggerBundle.message("label.debugger.general.configurable.evaluate.finally.on.pop")), BorderLayout.WEST);
    panel.add(evalFinallyPanel, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, NONE, new Insets(4, cbLeftOffset, 0, 0), 0, 0));

    myCbResumeOnlyCurrentThread = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.resume.only.current.thread"));
    panel.add(myCbResumeOnlyCurrentThread, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }
}