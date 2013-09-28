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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.SurroundWithIfFix;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiExpression;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class DataFlowInspection extends DataFlowInspectionBase {
  @Override
  protected void addSurroundWithIfFix(PsiExpression qualifier, List<LocalQuickFix> fixes) {
    if (SurroundWithIfFix.isAvailable(qualifier)) {
      fixes.add(new SurroundWithIfFix(qualifier));
    }
  }
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myIgnoreAssertions;
    private final JCheckBox myReportConstantReferences;
    private final JCheckBox mySuggestNullables;
    private final JCheckBox myDontReportTrueAsserts;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      mySuggestNullables = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.nullable.quickfix.option"));
      mySuggestNullables.setSelected(SUGGEST_NULLABLE_ANNOTATIONS);
      mySuggestNullables.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          SUGGEST_NULLABLE_ANNOTATIONS = mySuggestNullables.isSelected();
        }
      });

      myDontReportTrueAsserts = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.true.asserts.option"));
      myDontReportTrueAsserts.setSelected(DONT_REPORT_TRUE_ASSERT_STATEMENTS);
      myDontReportTrueAsserts.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          DONT_REPORT_TRUE_ASSERT_STATEMENTS = myDontReportTrueAsserts.isSelected();
        }
      });
      
      myIgnoreAssertions = new JCheckBox("Ignore assert statements");
      myIgnoreAssertions.setSelected(IGNORE_ASSERT_STATEMENTS);
      myIgnoreAssertions.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          IGNORE_ASSERT_STATEMENTS = myIgnoreAssertions.isSelected();
        }
      });

      myReportConstantReferences = new JCheckBox("Warn when reading a value guaranteed to be constant");
      myReportConstantReferences.setSelected(REPORT_CONSTANT_REFERENCE_VALUES);
      myReportConstantReferences.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          REPORT_CONSTANT_REFERENCE_VALUES = myReportConstantReferences.isSelected();
        }
      });

      gc.insets = new Insets(0, 0, 0, 0);
      gc.gridy = 0;
      add(mySuggestNullables, gc);

      final JButton configureAnnotations = new JButton(InspectionsBundle.message("configure.annotations.option"));
      configureAnnotations.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(OptionsPanel.this));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          final NullableNotNullDialog dialog = new NullableNotNullDialog(project);
          dialog.show();
        }
      });
      gc.gridy++;
      gc.fill = GridBagConstraints.NONE;
      gc.insets.left = 20;
      gc.insets.bottom = 15;
      add(configureAnnotations, gc);

      if ("true".equals(System.getProperty("dfa.inspection.show.method.configuration", "false"))) {
        final JButton configureCheckAnnotations = new JButton(InspectionsBundle.message("configure.checker.option.button"));
        configureCheckAnnotations.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(OptionsPanel.this));
            if (project == null) project = ProjectManager.getInstance().getDefaultProject();
            final ConditionCheckDialog dialog = new ConditionCheckDialog(project,
                                                                         InspectionsBundle.message("configure.checker.option.main.dialog.title")
            );
            dialog.show();
          }
        });
        gc.gridy++;
        gc.fill = GridBagConstraints.NONE;
        gc.insets.left = 20;
        gc.insets.bottom = 15;
        add(configureCheckAnnotations, gc);
      }

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 1;
      gc.insets.left = 0;
      gc.gridy++;
      add(myDontReportTrueAsserts, gc);

      gc.gridy++;
      add(myIgnoreAssertions, gc);

      gc.gridy++;
      add(myReportConstantReferences, gc);
    }
  }

}
