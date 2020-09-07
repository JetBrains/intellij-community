package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkVersion;
import com.intellij.framework.PresentableVersion;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class FrameworkVersionComponent {
  private final JPanel myMainPanel;
  private final FrameworkSupportModelBase myModel;
  private final List<? extends FrameworkVersion> myAllVersions;
  private final JPanel myVersionsPanel;
  private final ComboBox<FrameworkVersion> myVersionsBox;
  private final String myFrameworkOrGroupId;

  public FrameworkVersionComponent(final FrameworkSupportModelBase model, final String frameworkOrGroupId,
                                   final List<? extends FrameworkVersion> versions_, @NlsContexts.Label String labelText) {
    myModel = model;
    myAllVersions = versions_;
    myMainPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 3, true, false));
    myFrameworkOrGroupId = frameworkOrGroupId;
    myVersionsBox = new ComboBox<>();
    myVersionsBox.setRenderer(SimpleListCellRenderer.create("", PresentableVersion::getPresentableName));
    myVersionsBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FrameworkVersion selectedVersion = getSelectedVersion();
        if (selectedVersion != null) {
          model.setSelectedVersion(frameworkOrGroupId, selectedVersion);
        }
      }
    });

    myVersionsPanel = FormBuilder.createFormBuilder().setHorizontalGap(5).addLabeledComponent(labelText, myVersionsBox).getPanel();
    myMainPanel.add(myVersionsPanel);
    updateVersionsList();
  }

  private FrameworkVersion getSelectedVersion() {
    return (FrameworkVersion)myVersionsBox.getSelectedItem();
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void updateVersionsList() {
    FrameworkVersion oldSelection = getSelectedVersion();
    List<? extends FrameworkVersion> versions = computeAvailableVersions();
    myVersionsBox.removeAllItems();
    for (FrameworkVersion version : versions) {
      myVersionsBox.addItem(version);
    }
    myVersionsPanel.setVisible(!versions.isEmpty());
    if (!versions.isEmpty()) {
      FrameworkVersion toSelect = oldSelection != null && versions.contains(oldSelection) ? oldSelection : versions.get(versions.size() - 1);
      myVersionsBox.setSelectedItem(toSelect);
      myModel.setSelectedVersion(myFrameworkOrGroupId, toSelect);
    }
  }

  private List<FrameworkVersion> computeAvailableVersions() {
    List<FrameworkVersion> versions = new ArrayList<>();
    for (FrameworkVersion version : myAllVersions) {
      if (version.getAvailabilityCondition().isAvailableFor(myModel)) {
        versions.add(version);
      }
    }
    return versions;
  }
}
