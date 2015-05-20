/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NullableStuffInspection extends NullableStuffInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myBreakingOverriding;
    private JCheckBox myNAMethodOverridesNN;
    private JPanel myPanel;
    private JCheckBox myReportNotAnnotatedGetter;
    private JButton myConfigureAnnotationsButton;
    private JCheckBox myIgnoreExternalSuperNotNull;
    private JCheckBox myNNParameterOverridesNA;
    private JCheckBox myRequireNNFieldsInitialized;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myBreakingOverriding.addActionListener(actionListener);
      myNNParameterOverridesNA.addActionListener(actionListener);
      myReportNotAnnotatedGetter.addActionListener(actionListener);
      myIgnoreExternalSuperNotNull.addActionListener(actionListener);
      myRequireNNFieldsInitialized.addActionListener(actionListener);
      myConfigureAnnotationsButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(OptionsPanel.this));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          final NullableNotNullDialog dialog = new NullableNotNullDialog(project);
          dialog.show();
        }
      });
      reset();
    }

    private void reset() {
      myBreakingOverriding.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myReportNotAnnotatedGetter.setSelected(REPORT_NOT_ANNOTATED_GETTER);
      myIgnoreExternalSuperNotNull.setSelected(IGNORE_EXTERNAL_SUPER_NOTNULL);
      myNNParameterOverridesNA.setSelected(REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED);
      myRequireNNFieldsInitialized.setSelected(REQUIRE_NOTNULL_FIELDS_INITIALIZED);

      myIgnoreExternalSuperNotNull.setEnabled(myNAMethodOverridesNN.isSelected());
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myBreakingOverriding.isSelected();
      REPORT_NOT_ANNOTATED_GETTER = myReportNotAnnotatedGetter.isSelected();
      IGNORE_EXTERNAL_SUPER_NOTNULL = myIgnoreExternalSuperNotNull.isSelected();
      REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = myNNParameterOverridesNA.isSelected();
      REQUIRE_NOTNULL_FIELDS_INITIALIZED = myRequireNNFieldsInitialized.isSelected();
      REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL;

      myIgnoreExternalSuperNotNull.setEnabled(myNAMethodOverridesNN.isSelected());
    }
  }
}
