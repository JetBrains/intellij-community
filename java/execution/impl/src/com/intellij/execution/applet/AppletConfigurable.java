/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.applet;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.CheckableRunConfigurationEditor;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
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
      public String valueOf(final AppletParameter appletParameter) {
        return appletParameter.getName();
      }

      public void setValue(final AppletParameter appletParameter, final String name) {
        appletParameter.setName(name);
      }
    },
    new MyColumnInfo(ExecutionBundle.message("applet.configuration.parameter.value.column")) {
      public String valueOf(final AppletParameter appletParameter) {
        return appletParameter.getValue();
      }

      public void setValue(final AppletParameter appletParameter, final String value) {
        appletParameter.setValue(value);
      }
    }
  };
  private final ListTableModel<AppletParameter> myParameters;
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
    final ArrayList<AppletParameter> newItems =
      new ArrayList<>(myParameters.getItems());
    final AppletParameter parameter = new AppletParameter("newParameter", "");
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

  private static List<AppletParameter> cloneParameters(@NotNull List<AppletParameter> items) {
    List<AppletParameter> params = new SmartList<>();
    for (AppletParameter appletParameter : items) {
      params.add(new AppletParameter(appletParameter.getName(), appletParameter.getValue()));
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

  private static String toSystemFormat(String s) {
    s = s.trim();
    return s.length() == 0 ? null : s.replace(File.separatorChar, '/');
  }

  public void applyEditorTo(@NotNull final AppletConfiguration configuration) {
    checkEditorData(configuration);
    myTable.stopEditing();
    configuration.getOptions().setAppletParameters(cloneParameters(myParameters.getItems()));
  }

  public void resetEditorFrom(@NotNull AppletConfiguration runConfiguration) {
    AppletConfigurationOptions configuration = runConfiguration.getOptions();
    getClassNameComponent().setText(configuration.getMainClassName());
    String presentableHtmlName = configuration.getHtmlFileName();
    if (presentableHtmlName != null && !StringUtil.startsWithIgnoreCase(presentableHtmlName, HTTP_PREFIX)) {
      presentableHtmlName = presentableHtmlName.replace('/', File.separatorChar);
    }
    getHtmlPathComponent().setText(presentableHtmlName);
    getPolicyFileComponent().setText(runConfiguration.getPolicyFile());
    getVMParametersComponent().setText(configuration.getVmParameters());
    getWidthComponent().setText(Integer.toString(configuration.getWidth()));
    getHeightComponent().setText(Integer.toString(configuration.getHeight()));

    (configuration.getHtmlUsed() ? myURL : myMainClass).setSelected(true);
    changePanel();

    myParameters.setItems(cloneParameters(configuration.getAppletParameters()));
    myModuleSelector.reset(runConfiguration);
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
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

  public void checkEditorData(@NotNull AppletConfiguration runConfiguration) {
    AppletConfigurationOptions configuration = runConfiguration.getOptions();
    runConfiguration.setMainClassName(getClassNameComponent().getText().trim());
    configuration.setHtmlFileName(toSystemFormat(getHtmlPathComponent().getText()));
    configuration.setVmParameters(getVMParametersComponent().getText().trim());
    runConfiguration.setPolicyFile(getPolicyFileComponent().getText());
    myModuleSelector.applyTo(runConfiguration);
    try {
      configuration.setWidth(Integer.parseInt(getWidthComponent().getText()));
    }
    catch (NumberFormatException ignored) {
    }
    try {
      configuration.setHeight(Integer.parseInt(getHeightComponent().getText()));
    }
    catch (NumberFormatException ignored) {
    }
    configuration.setHtmlUsed(myURL.isSelected());
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
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

  private static abstract class MyColumnInfo extends ColumnInfo<AppletParameter, String> {
    public MyColumnInfo(final String name) {
      super(name);
    }

    public TableCellEditor getEditor(final AppletParameter item) {
      final JTextField textField = new JTextField();
      textField.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      return new DefaultCellEditor(textField);
    }

    public boolean isCellEditable(final AppletParameter appletParameter) {
      return true;
    }
  }
}
