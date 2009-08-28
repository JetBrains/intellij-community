/*
 * User: anna
 * Date: 19-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.SeverityEditorDialog;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.ComboboxWithBrowseButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TreeSet;

public class LevelChooser extends ComboboxWithBrowseButton {
  private final MyRenderer ourRenderer = new MyRenderer();

  public LevelChooser(final SeverityRegistrar severityRegistrar) {
    final JComboBox comboBox = getComboBox();
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    comboBox.setModel(model);
    fillModel(model, severityRegistrar);
    comboBox.setRenderer(ourRenderer);
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SeverityEditorDialog dlg = new SeverityEditorDialog(LevelChooser.this, (HighlightSeverity)getComboBox().getSelectedItem(), severityRegistrar);
        dlg.show();
        if (dlg.isOK()) {
          final Object item = getComboBox().getSelectedItem();
          fillModel(model, severityRegistrar);
          final HighlightInfoType type = dlg.getSelectedType();
          if (type != null) {
            getComboBox().setSelectedItem(type.getSeverity(null));
          } else {
            getComboBox().setSelectedItem(item);
          }
        }
      }
    });
  }

  private static void fillModel(DefaultComboBoxModel model, final SeverityRegistrar severityRegistrar) {
    model.removeAllElements();
    final TreeSet<HighlightSeverity> severities = new TreeSet<HighlightSeverity>(severityRegistrar);
    for (SeverityRegistrar.SeverityBasedTextAttributes type : severityRegistrar.getRegisteredHighlightingInfoTypes()) {
      severities.add(type.getSeverity());
    }
    severities.add(HighlightSeverity.ERROR);
    severities.add(HighlightSeverity.WARNING);
    severities.add(HighlightSeverity.INFO);
    severities.add(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING);
    for (HighlightSeverity severity : severities) {
      model.addElement(severity);
    }
  }

  public HighlightDisplayLevel getLevel() {
    HighlightSeverity severity = (HighlightSeverity)getComboBox().getSelectedItem();
    if (severity == null) return HighlightDisplayLevel.WARNING;
    return HighlightDisplayLevel.find(severity);
  }

  public void setLevel(HighlightDisplayLevel level) {
    getComboBox().setSelectedItem(level.getSeverity());
  }

  private static class MyRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof HighlightSeverity) {
        HighlightSeverity severity = (HighlightSeverity)value;
        setText(SingleInspectionProfilePanel.renderSeverity(severity));
        setIcon(HighlightDisplayLevel.find(severity).getIcon());
      }
      return this;
    }
  }
}