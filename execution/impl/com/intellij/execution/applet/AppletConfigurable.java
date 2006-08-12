package com.intellij.execution.applet;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.CheckableRunConfigurationEditor;
import com.intellij.execution.junit2.configuration.ClassBrowser;
import com.intellij.execution.junit2.configuration.ConfigurationModuleSelector;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.exclude.ComparablesComparator;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class AppletConfigurable extends SettingsEditor<AppletConfiguration> implements CheckableRunConfigurationEditor<AppletConfiguration>{
  private JPanel myWholePanel;
  private JRadioButton myMainClass;
  private JRadioButton myURL;
  private JPanel myClassOptions;
  private JPanel myHTMLOptions;
  private LabeledComponent<TextFieldWithBrowseButton> myPolicyFile;
  private LabeledComponent<RawCommandLineEditor> myVMParameters;
  private TextFieldWithBrowseButton myClassName;
  private TextFieldWithBrowseButton myHtmlFile;
  private JTextField myWidth;
  private JTextField myHeight;
  private LabeledComponent<JComboBox> myModule;
  private JPanel myTablePlace;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JLabel myHtmlFileLabel;
  private JLabel myClassNameLabel;
  private JLabel myWidthLabel;
  private JLabel myHeightLabel;
  private AlternativeJREPanel myAlternativeJREPanel;
  private ButtonGroup myAppletRadioButtonGroup = new ButtonGroup();

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  private static final ColumnInfo[] PARAMETER_COLUMNS = new ColumnInfo[]{
    new MyColumnInfo(ExecutionBundle.message("applet.configuration.parameter.name.column")){
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
  private final ListTableModel<AppletConfiguration.AppletParameter> myParameters = new ListTableModel<AppletConfiguration.AppletParameter>(PARAMETER_COLUMNS);
  private final TableView myTable;
  @NonNls
  protected static final String HTTP_PREFIX = "http:/";

  private void changePanel () {
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
    myClassNameLabel.setLabelFor(myClassName.getTextField());
    myHtmlFileLabel.setLabelFor(myHtmlFile.getTextField());
    myWidthLabel.setLabelFor(myWidth);
    myHeightLabel.setLabelFor(myHeight);

    myProject = project;
    myModuleSelector = new ConfigurationModuleSelector(project, getModuleComponent());
    myTablePlace.setLayout(new BorderLayout());
    myTable = new TableView(myParameters);
    myTablePlace.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myAppletRadioButtonGroup.add(myMainClass);
    myAppletRadioButtonGroup.add(myURL);
    getVMParametersComponent().setDialodCaption(myVMParameters.getRawText());

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

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addParameter();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        removeParameter();
      }
    });
  }

  private void removeParameter() {
    final int selectedRow = myTable.getSelectedRow();
    if (selectedRow < 0 || selectedRow >= myTable.getRowCount()) return;
    final ArrayList<AppletConfiguration.AppletParameter> newItems =
      new ArrayList<AppletConfiguration.AppletParameter>(myParameters.getItems());
    newItems.remove(selectedRow);
    myParameters.setItems(newItems);
  }

  private void addParameter() {
    final ArrayList<AppletConfiguration.AppletParameter> newItems =
      new ArrayList<AppletConfiguration.AppletParameter>(myParameters.getItems());
    newItems.add(new AppletConfiguration.AppletParameter("newParameter", ""));
    myParameters.setItems(newItems);
  }

  private JComboBox getModuleComponent() {
    return myModule.getComponent();
  }

  private TextFieldWithBrowseButton getPolicyFileComponent() {
    return myPolicyFile.getComponent();
  }

  private void getConfigurationTo(final AppletConfiguration configuration) {

  }

  private List<AppletConfiguration.AppletParameter> cloneParameters(final List<AppletConfiguration.AppletParameter> items) {
    final List<AppletConfiguration.AppletParameter> params = new ArrayList<AppletConfiguration.AppletParameter>();
    for (Iterator<AppletConfiguration.AppletParameter> iterator = items.iterator(); iterator.hasNext();) {
      AppletConfiguration.AppletParameter appletParameter = iterator.next();
      params.add(new AppletConfiguration.AppletParameter(appletParameter.getName(), appletParameter.getValue()));
    }
    return params;
  }

  private JTextField getWidthComponent() {
    return myWidth;
  }

  private TextFieldWithBrowseButton getClassNameComponent() {
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
    myAlternativeJREPanel.init(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);
  }

  private RawCommandLineEditor getVMParametersComponent() {
    return myVMParameters.getComponent();
  }

  private JTextField getHeightComponent() {
    return myHeight;
  }


  public JComponent createEditor() {
    return myWholePanel;
  }

  public void disposeEditor() {
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
    configuration.ALTERNATIVE_JRE_PATH = myAlternativeJREPanel.getPath();
    configuration.ALTERNATIVE_JRE_PATH_ENABLED = myAlternativeJREPanel.isPathEnabled();
  }

  private static abstract class MyColumnInfo extends ColumnInfo<AppletConfiguration.AppletParameter, String> {
    private static final ComparablesComparator<String> COMPARATOR = new ComparablesComparator<String>();

    public MyColumnInfo(final String name) {
      super(name);
    }

    public Comparator<AppletConfiguration.AppletParameter> getComparator() {
      return new Comparator<AppletConfiguration.AppletParameter>() {
        public int compare(final AppletConfiguration.AppletParameter parameter1,
                           final AppletConfiguration.AppletParameter parameter2) {
          return COMPARATOR.compare(valueOf(parameter1), valueOf(parameter2));
        }
      };
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
