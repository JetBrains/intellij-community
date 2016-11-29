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
package com.intellij.execution.applet;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.CheckableRunConfigurationEditor;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppletConfigurable extends SettingsEditor<AppletConfiguration> implements CheckableRunConfigurationEditor<AppletConfiguration>,
                                                                                       PanelWithAnchor {
  private JPanel myWholePanel;
  private JRadioButton myMainClass;
  private JRadioButton myURL;
  private JPanel myClassOptions;
  private JPanel myHTMLOptions;
  private LabeledComponent<TextFieldWithBrowseButton> myPolicyFile;
  private LabeledComponent<RawCommandLineEditor> myVMParameters;
  private EditorTextFieldWithBrowseButton myClassName;
  private TextFieldWithBrowseButton myHtmlFile;
  private JTextField myWidth;
  private JTextField myHeight;
  private LabeledComponent<ModulesComboBox> myModule;
  private JPanel myTablePlace;
  private JBLabel myHtmlFileLabel;
  private JBLabel myClassNameLabel;
  private JBLabel myWidthLabel;
  private JLabel myHeightLabel;
  private JrePathEditor myJrePathEditor;
  private final ButtonGroup myAppletRadioButtonGroup;
  private JComponent anchor;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  private static final ColumnInfo[] PARAMETER_COLUMNS = new ColumnInfo[]{
    new MyColumnInfo(ExecutionBundle.message("applet.configuration.parameter.name.column")) {
      public String valueOf(final AppletConfiguration.AppletParameter appletParameter) {
        return appletParameter.getName();
      }

      public void setValue(final AppletConfiguration.AppletParameter appletParameter, final String name) {
        appletParameter.setName(name);
      }
    },
    new MyColumnInfo(ExecutionBundle.message("applet.configuration.parameter.value.column")) {
      public String valueOf(final AppletConfiguration.AppletParameter appletParameter) {
        return appletParameter.getValue();
      }

      public void setValue(final AppletConfiguration.AppletParameter appletParameter, final String value) {
        appletParameter.setValue(value);
      }
    }
  };
  private final ListTableModel<AppletConfiguration.AppletParameter> myParameters;
  private final TableView myTable;
  @NonNls
  protected static final String HTTP_PREFIX = "http:/";

  private void changePanel() {
    if (myMainClass.isSelected()) {
      myClassOptions.setVisible(true);
      myHTMLOptions.setVisible(false);
    }
    else {
      myHTMLOptions.setVisible(true);
      myClassOptions.setVisible(false);
    }
  }

  public AppletConfigurable(final Project project) {
    myProject = project;
    myClassNameLabel.setLabelFor(myClassName.getChildComponent());
    myHtmlFileLabel.setLabelFor(myHtmlFile.getTextField());
    myWidthLabel.setLabelFor(myWidth);
    myHeightLabel.setLabelFor(myHeight);


    myModuleSelector = new ConfigurationModuleSelector(project, getModuleComponent());
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(getModuleComponent(), true));
    myTablePlace.setLayout(new BorderLayout());
    myParameters = new ListTableModel<>(PARAMETER_COLUMNS);
    myTable = new TableView(myParameters);
    myTable.getEmptyText().setText(ExecutionBundle.message("no.parameters"));
    myTablePlace.add(
      ToolbarDecorator.createDecorator(myTable)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            addParameter();
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeParameter();
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);
    myAppletRadioButtonGroup = new ButtonGroup();
    myAppletRadioButtonGroup.add(myMainClass);
    myAppletRadioButtonGroup.add(myURL);
    getVMParametersComponent().setDialogCaption(myVMParameters.getRawText());

    myMainClass.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        changePanel();
      }
    });
    myURL.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        changePanel();
      }
    });

    getPolicyFileComponent().addBrowseFolderListener(ExecutionBundle.message("select.applet.policy.file.dialog.title"), null, myProject,
                                                     FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    getHtmlPathComponent().addBrowseFolderListener(ExecutionBundle.message("choose.html.file.dialog.title"), null, myProject,
                                                   FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    ClassBrowser.createAppletClassBrowser(myProject, myModuleSelector).setField(getClassNameComponent());

    myHTMLOptions.setVisible(false);

    setAnchor(myVMParameters.getLabel());
  }

  private void removeParameter() {
    TableUtil.removeSelectedItems(myTable);
  }

  private void addParameter() {
    final ArrayList<AppletConfiguration.AppletParameter> newItems =
      new ArrayList<>(myParameters.getItems());
    final AppletConfiguration.AppletParameter parameter = new AppletConfiguration.AppletParameter("newParameter", "");
    newItems.add(parameter);
    myParameters.setItems(newItems);

    int index = newItems.size() - 1;
    myTable.getSelectionModel().setSelectionInterval(index, index);
    myTable.scrollRectToVisible(myTable.getCellRect(index, 0, true));
  }

  private ModulesComboBox getModuleComponent() {
    return myModule.getComponent();
  }

  private TextFieldWithBrowseButton getPolicyFileComponent() {
    return myPolicyFile.getComponent();
  }

  private static List<AppletConfiguration.AppletParameter> cloneParameters(final List<AppletConfiguration.AppletParameter> items) {
    final List<AppletConfiguration.AppletParameter> params = new ArrayList<>();
    for (AppletConfiguration.AppletParameter appletParameter : items) {
      params.add(new AppletConfiguration.AppletParameter(appletParameter.getName(), appletParameter.getValue()));
    }
    return params;
  }

  private JTextField getWidthComponent() {
    return myWidth;
  }

  private EditorTextFieldWithBrowseButton getClassNameComponent() {
    return myClassName;
  }

  private TextFieldWithBrowseButton getHtmlPathComponent() {
    return myHtmlFile;
  }

  private String toNull(String s) {
    s = s.trim();
    return s.length() == 0 ? null : s;
  }

  private String toSystemFormat(String s) {
    s = s.trim();
    return s.length() == 0 ? null : s.replace(File.separatorChar, '/');
  }

  public void applyEditorTo(final AppletConfiguration configuration) {
    checkEditorData(configuration);
    myTable.stopEditing();
    final List<AppletConfiguration.AppletParameter> params = cloneParameters(myParameters.getItems());
    configuration.setAppletParameters(params);
  }

  public void resetEditorFrom(final AppletConfiguration configuration) {
    getClassNameComponent().setText(configuration.MAIN_CLASS_NAME);
    String presentableHtmlName = configuration.HTML_FILE_NAME;
    if (presentableHtmlName != null && !StringUtil.startsWithIgnoreCase(presentableHtmlName, HTTP_PREFIX)) {
      presentableHtmlName = presentableHtmlName.replace('/', File.separatorChar);
    }
    getHtmlPathComponent().setText(presentableHtmlName);
    getPolicyFileComponent().setText(configuration.getPolicyFile());
    getVMParametersComponent().setText(configuration.VM_PARAMETERS);
    getWidthComponent().setText(Integer.toString(configuration.WIDTH));
    getHeightComponent().setText(Integer.toString(configuration.HEIGHT));

    (configuration.HTML_USED ? myURL : myMainClass).setSelected(true);
    changePanel();

    final AppletConfiguration.AppletParameter[] appletParameters = configuration.getAppletParameters();
    if (appletParameters != null) {
      myParameters.setItems(cloneParameters(Arrays.asList(appletParameters)));
    }
    myModuleSelector.reset(configuration);
    myJrePathEditor
      .setPathOrName(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);
  }

  private RawCommandLineEditor getVMParametersComponent() {
    return myVMParameters.getComponent();
  }

  private JTextField getHeightComponent() {
    return myHeight;
  }


  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  public void checkEditorData(final AppletConfiguration configuration) {
    configuration.MAIN_CLASS_NAME = toNull(getClassNameComponent().getText());
    configuration.HTML_FILE_NAME = toSystemFormat(getHtmlPathComponent().getText());
    configuration.VM_PARAMETERS = toNull(getVMParametersComponent().getText());
    configuration.setPolicyFile(getPolicyFileComponent().getText());
    myModuleSelector.applyTo(configuration);
    try {
      configuration.WIDTH = Integer.parseInt(getWidthComponent().getText());
    }
    catch (NumberFormatException e) {
    }
    try {
      configuration.HEIGHT = Integer.parseInt(getHeightComponent().getText());
    }
    catch (NumberFormatException e) {
    }
    configuration.HTML_USED = myURL.isSelected();
    configuration.ALTERNATIVE_JRE_PATH = myJrePathEditor.getJrePathOrName();
    configuration.ALTERNATIVE_JRE_PATH_ENABLED = myJrePathEditor.isAlternativeJreSelected();
  }

  private void createUIComponents() {
    myClassName = new EditorTextFieldWithBrowseButton(myProject, true);
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myModule.setAnchor(anchor);
    myPolicyFile.setAnchor(anchor);
    myVMParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myHtmlFileLabel.setAnchor(anchor);
  }

  private static abstract class MyColumnInfo extends ColumnInfo<AppletConfiguration.AppletParameter, String> {
    public MyColumnInfo(final String name) {
      super(name);
    }

    public TableCellEditor getEditor(final AppletConfiguration.AppletParameter item) {
      final JTextField textField = new JTextField();
      textField.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      return new DefaultCellEditor(textField);
    }

    public boolean isCellEditable(final AppletConfiguration.AppletParameter appletParameter) {
      return true;
    }
  }
}
