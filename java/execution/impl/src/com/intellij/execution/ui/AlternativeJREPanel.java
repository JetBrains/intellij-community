/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.PathUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

/**
 * User: anna
 *
 * @deprecated use {@link JrePathEditor} instead
 */
public class AlternativeJREPanel extends JPanel implements PanelWithAnchor {
  private final ComponentWithBrowseButton<TextFieldWithHistory> myPathField;
  private final JBCheckBox myCbEnabled;
  final TextFieldWithHistory myFieldWithHistory;
  private JComponent myAnchor;

  public AlternativeJREPanel() {
    myCbEnabled = new JBCheckBox(ExecutionBundle.message("run.configuration.use.alternate.jre.checkbox"));

    myFieldWithHistory = new TextFieldWithHistory();
    myFieldWithHistory.setHistorySize(-1);
    final ArrayList<String> foundJDKs = new ArrayList<>();
    final Sdk[] allJDKs = ProjectJdkTable.getInstance().getAllJdks();

    for (Sdk sdk : allJDKs) {
      foundJDKs.add(sdk.getName());
    }

    for (JreProvider provider : JreProvider.EP_NAME.getExtensions()) {
      String path = provider.getJrePath();
      if (!StringUtil.isEmpty(path)) {
        foundJDKs.add(path);
      }
    }

    for (Sdk jdk : allJDKs) {
      String homePath = jdk.getHomePath();

      if (!SystemInfo.isMac) {
        final File jre = new File(jdk.getHomePath(), "jre");
        if (jre.isDirectory()) {
          homePath = jre.getPath();
        }
      }

      if (!foundJDKs.contains(homePath)) {
        foundJDKs.add(homePath);
      }
    }
    myFieldWithHistory.setHistory(foundJDKs);
    myPathField = new ComponentWithBrowseButton<>(myFieldWithHistory, null);
    myPathField.addBrowseFolderListener(ExecutionBundle.message("run.configuration.select.alternate.jre.label"),
                                        ExecutionBundle.message("run.configuration.select.jre.dir.label"),
                                        null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                        TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);

    setLayout(new MigLayout("ins 0, gap 10, fill, flowx"));
    add(myCbEnabled, "shrinkx");
    add(myPathField, "growx, pushx");

    InsertPathAction.addTo(myFieldWithHistory.getTextEditor());

    myCbEnabled.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enabledChanged();
      }
    });
    enabledChanged();

    setAnchor(myCbEnabled);

    updateUI();
  }

  private void enabledChanged() {
    final boolean pathEnabled = isPathEnabled();
    GuiUtils.enableChildren(myPathField, pathEnabled);
    myFieldWithHistory.invalidate(); //need to revalidate inner component
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName(myPathField.getChildComponent().getText().trim());
  }

  private void setPath(@Nullable String path) {
    myPathField.getChildComponent().setText(StringUtil.notNullize(PathUtil.toSystemDependentName(path)));
  }

  public boolean isPathEnabled() {
    return myCbEnabled.isSelected();
  }

  private void setPathEnabled(boolean b) {
    myCbEnabled.setSelected(b);
    enabledChanged();
  }

  public void init(@Nullable String path, boolean isEnabled){
    setPathEnabled(isEnabled);
    setPath(path);
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    myAnchor = anchor;
    myCbEnabled.setAnchor(anchor);
  }
}
