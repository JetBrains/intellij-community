package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkGroup;
import com.intellij.framework.FrameworkGroupVersion;
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
public class FrameworkGroupOptionsComponent {
  private final JPanel myMainPanel;

  public FrameworkGroupOptionsComponent(final FrameworkGroup<?> group, final FrameworkSupportModelBase model) {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    List<? extends FrameworkGroupVersion> versions = group.getGroupVersions();
    if (!versions.isEmpty()) {
      final ComboBox versionsBox = new ComboBox();
      versionsBox.setRenderer(new ListCellRendererWrapper<FrameworkGroupVersion>() {
        @Override
        public void customize(JList list, FrameworkGroupVersion value, int index, boolean selected, boolean hasFocus) {
          setText(value != null ? value.getPresentableName() : "");
        }
      });
      versionsBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          model.setSelectedVersion(group, (FrameworkGroupVersion)versionsBox.getSelectedItem());
        }
      });
      for (FrameworkGroupVersion version : versions) {
        versionsBox.addItem(version);
      }
      FrameworkGroupVersion latestVersion = versions.get(versions.size() - 1);
      versionsBox.setSelectedItem(latestVersion);
      model.setSelectedVersion(group, latestVersion);
      panel.add(FormBuilder.createFormBuilder().addLabeledComponent("Version:", versionsBox).getPanel());
    }
    myMainPanel = panel;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
