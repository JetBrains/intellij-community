/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.defaultFileTemplateUsage;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author Alexey
 */
public class InspectionOptions {
  private JPanel myPanel;
  private JCheckBox inspectFileHeader;
  private JCheckBox inspectMethodBody;
  private JCheckBox inspectCatchSection;

  public InspectionOptions(final DefaultFileTemplateUsageInspection inspection) {
    inspectCatchSection.setSelected(inspection.CHECK_TRY_CATCH_SECTION);
    inspectMethodBody.setSelected(inspection.CHECK_METHOD_BODY);
    inspectFileHeader.setSelected(inspection.CHECK_FILE_HEADER);

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
         inspection.CHECK_FILE_HEADER = inspectFileHeader.isSelected();
         inspection.CHECK_METHOD_BODY = inspectMethodBody.isSelected();
         inspection.CHECK_TRY_CATCH_SECTION = inspectCatchSection.isSelected();
      }
    };
    inspectCatchSection.addActionListener(listener);
    inspectFileHeader.addActionListener(listener);
    inspectMethodBody.addActionListener(listener);
  }

  public JPanel getComponent() {
    return myPanel;
  }
}
