package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.TextFieldWithHistory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Jun 21, 2005
 */
public class AlternativeJREPanel extends JPanel{
  private ComponentWithBrowseButton<TextFieldWithHistory> myPathField;
  private JCheckBox myCbEnabled;
  final TextFieldWithHistory myFieldWithHistory;

  public AlternativeJREPanel() {
    super(new GridBagLayout());
    myCbEnabled = new JCheckBox(ExecutionBundle.message("run.configuration.use.alternate.jre.checkbox"));
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, new Insets(2, -2, 2, 2), 0, 0);
    add(myCbEnabled, gc);

    myFieldWithHistory = new TextFieldWithHistory();
    myFieldWithHistory.setBorder(BorderFactory.createEtchedBorder());
    final ArrayList<String> foundJdks = new ArrayList<String>();
    final ProjectJdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
    for (ProjectJdk jdk : allJdks) {
      foundJdks.add(jdk.getHomePath());
    }
    myFieldWithHistory.setHistory(foundJdks);
    myPathField = new ComponentWithBrowseButton<TextFieldWithHistory>(myFieldWithHistory, null);
    myPathField.addBrowseFolderListener(ExecutionBundle.message("run.configuration.select.alternate.jre.label"),
                                        ExecutionBundle.message("run.configuration.select.jre.dir.label"),
                                        null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR, TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    gc.insets.left = 20;
    add(myPathField, gc);
    InsertPathAction.addTo(myFieldWithHistory.getTextEditor());

    gc.weighty = 1;
    add(Box.createVerticalBox(), gc);

    myCbEnabled.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enabledChanged();
      }
    });
    enabledChanged();
  }

  private void enabledChanged() {
    final boolean pathEnabled = isPathEnabled();
    GuiUtils.enableChildren(myPathField, pathEnabled);
    myFieldWithHistory.invalidate(); //need to revalidate inner component
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName(myPathField.getChildComponent().getText().trim());
  }

  private void setPath(final String path) {
    myPathField.getChildComponent().setText(FileUtil.toSystemDependentName(path == null ? "" : path));
  }

  public boolean isPathEnabled() {
    return myCbEnabled.isSelected();
  }

  private void setPathEnabled(boolean b) {
    myCbEnabled.setSelected(b);
    enabledChanged();
  }

  public void init(String path, boolean isEnabled){
    setPathEnabled(isEnabled);
    setPath(path);
  }
}

