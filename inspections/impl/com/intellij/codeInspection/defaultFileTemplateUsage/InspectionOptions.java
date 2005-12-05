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
