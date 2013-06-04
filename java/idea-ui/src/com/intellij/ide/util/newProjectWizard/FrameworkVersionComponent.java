package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkVersion;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author nik
 */
public class FrameworkVersionComponent {
  private final JPanel myMainPanel;

  public FrameworkVersionComponent(final FrameworkSupportModelBase model, final String frameworkOrGroupId,
                                   final List<? extends FrameworkVersion> versions) {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    if (!versions.isEmpty()) {
      final ComboBox versionsBox = new ComboBox();
      versionsBox.setRenderer(new ListCellRendererWrapper<FrameworkVersion>() {
        @Override
        public void customize(JList list, FrameworkVersion value, int index, boolean selected, boolean hasFocus) {
          setText(value != null ? value.getPresentableName() : "");
        }
      });
      versionsBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          model.setSelectedVersion(frameworkOrGroupId, (FrameworkVersion)versionsBox.getSelectedItem());
        }
      });
      for (FrameworkVersion version : versions) {
        versionsBox.addItem(version);
      }
      FrameworkVersion latestVersion = versions.get(versions.size() - 1);
      versionsBox.setSelectedItem(latestVersion);
      model.setSelectedVersion(frameworkOrGroupId, latestVersion);
      panel.add(FormBuilder.createFormBuilder().addLabeledComponent("Version:", versionsBox).getPanel());
    }
    myMainPanel = panel;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
