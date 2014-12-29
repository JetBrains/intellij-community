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

package com.intellij.tools;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Consumer;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

public class ToolEditorDialog extends DialogWrapper {
  private final JTextField myNameField = new JTextField();
  private final JTextField myDescriptionField = new JTextField();
  private final ComboBox myGroupCombo = new ComboBox(-1);
  private final JCheckBox myShowInMainMenuCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.main.checkbox"));
  private final JCheckBox myShowInEditorCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.editor.checkbox"));
  private final JCheckBox myShowInProjectTreeCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.project.checkbox"));
  private final JCheckBox myShowInSearchResultsPopupCheckbox = new JCheckBox(ToolsBundle.message("tools.menu.search.checkbox"));
  private final JCheckBox myUseConsoleCheckbox = new JCheckBox(ToolsBundle.message("tools.open.console.checkbox"));
  private final JCheckBox myShowConsoleOnStdOutCheckbox = new JCheckBox(ExecutionBundle.message("logs.show.console.on.stdout"));
  private final JCheckBox myShowConsoleOnStdErrCheckbox = new JCheckBox(ExecutionBundle.message("logs.show.console.on.stderr"));
  private final JCheckBox mySynchronizedAfterRunCheckbox = new JCheckBox(ToolsBundle.message("tools.synchronize.files.checkbox"));
  private boolean myEnabled;

  // command fields
  private final JTextField myTfCommandWorkingDirectory = new JTextField();
  private final JTextField myTfCommand = new JTextField();
  private final JTextField myParametersField = new JTextField();
  private JButton myInsertWorkingDirectoryMacroButton;
  private JButton myInsertCommandMacroButton;
  private JButton myInsertParametersMacroButton;

  private final JButton myOutputFiltersButton;
  // panels
  private final JPanel mySimpleProgramPanel = createCommandPane();
  private FilterInfo[] myOutputFilters;
  private final Project myProject;

  @Override
  @NotNull
  protected JPanel createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    // name and group
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.name.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 0;
    constr.weightx = 1;
    constr.insets = new Insets(5, 10, 0, 10);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myNameField, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 10, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.group.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 3;
    constr.gridy = 0;
    constr.weightx = 0.7;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myGroupCombo, constr);
    myGroupCombo.setEditable(true);
    myGroupCombo.setFont(myNameField.getFont());
    Dimension comboSize = myNameField.getPreferredSize();
    myGroupCombo.setMinimumSize(comboSize);
    myGroupCombo.setPreferredSize(comboSize);

    // description

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 1;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(new JLabel(ToolsBundle.message("tools.description.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.weightx = 1;
    constr.gridwidth = 3;
    constr.insets = new Insets(5, 10, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(myDescriptionField, constr);

    // check boxes
    JPanel panel0 = new JPanel(new BorderLayout());
    panel0.add(getOptionsPanel(), BorderLayout.NORTH);
    panel0.add(getShowInPanel(), BorderLayout.SOUTH);

    constr = new GridBagConstraints();
    constr.gridy = 4;
    constr.gridwidth = 4;
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(panel0, constr);

    // custom panels (put into same place)
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 7/*8*/;
    constr.gridwidth = 4;
    constr.fill = GridBagConstraints.BOTH;
    constr.weightx = 1.0;
    constr.weighty = 1.0;
    constr.anchor = GridBagConstraints.NORTH;
    panel.add(mySimpleProgramPanel, constr);

    return panel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getIdForHelpAction());
  }

  protected String getIdForHelpAction() {
    return "preferences.externalToolsEdit";
  }

  protected ToolEditorDialog(JComponent parent, String title) {
    super(parent, true);

    myOutputFiltersButton = new JButton(ToolsBundle.message("tools.filters.button"));

    DataContext dataContext = DataManager.getInstance().getDataContext(parent);
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    MacroManager.getInstance().cacheMacrosPreview(dataContext);
    setTitle(title);
    init();
    addListeners();
    myShowConsoleOnStdOutCheckbox.setVisible(false);
    myShowConsoleOnStdErrCheckbox.setVisible(false);
  }

  private JPanel createCommandPane() {
    final JPanel pane = new JPanel(new GridBagLayout());
    pane.setBorder(IdeBorderFactory.createTitledBorder(ToolsBundle.message("tools.tool.group"), true));
    GridBagConstraints constr;

    // program

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.insets = new Insets(0, 0, 0, 10);
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    pane.add(new JLabel(ToolsBundle.message("tools.program.label")), constr);

    FixedSizeButton browseCommandButton = new FixedSizeButton(myTfCommand);

    addCommandBrowseAction(pane, browseCommandButton, myTfCommand);

    JPanel _pane0 = new JPanel(new BorderLayout());
    _pane0.add(myTfCommand, BorderLayout.CENTER);
    _pane0.add(browseCommandButton, BorderLayout.EAST);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseCommandButton, myTfCommand);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 0;
    constr.insets = new Insets(0, 0, 0, 10);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    constr.weightx = 1.0;
    pane.add(_pane0, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 0;
    constr.insets = new Insets(0, 0, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    myInsertCommandMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button"));
    pane.add(myInsertCommandMacroButton, constr);

    // parameters

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 1;
    constr.insets = new Insets(5, 0, 0, 10);
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    pane.add(new JLabel(ToolsBundle.message("tools.parameters.label")), constr);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.insets = new Insets(5, 0, 0, 10);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    constr.weightx = 1.0;
    pane.add(myParametersField, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 1;
    constr.insets = new Insets(5, 0, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    myInsertParametersMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button.a"));
    pane.add(myInsertParametersMacroButton, constr);

    // working directory

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 2;
    constr.insets = new Insets(5, 0, 0, 10);
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    pane.add(new JLabel(ToolsBundle.message("tools.working.directory.label")), constr);

    FixedSizeButton browseDirectoryButton = new FixedSizeButton(myTfCommandWorkingDirectory);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(browseDirectoryButton, myTfCommandWorkingDirectory);
    addWorkingDirectoryBrowseAction(pane, browseDirectoryButton, myTfCommandWorkingDirectory);

    JPanel _pane1 = new JPanel(new BorderLayout());
    _pane1.add(myTfCommandWorkingDirectory, BorderLayout.CENTER);
    _pane1.add(browseDirectoryButton, BorderLayout.EAST);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 2;
    constr.gridwidth = 1;
    constr.insets = new Insets(5, 0, 0, 10);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;
    constr.weightx = 1.0;
    pane.add(_pane1, constr);

    constr = new GridBagConstraints();
    constr.gridx = 2;
    constr.gridy = 2;
    constr.insets = new Insets(5, 0, 0, 0);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.BASELINE_LEADING;
    myInsertWorkingDirectoryMacroButton = new JButton(ToolsBundle.message("tools.insert.macro.button.c"));
    pane.add(myInsertWorkingDirectoryMacroButton, constr);

    // for normal resizing
    constr = new GridBagConstraints();
    constr.gridy = 3;
    constr.fill = GridBagConstraints.BASELINE_LEADING;
    constr.weighty = 1.0;
    pane.add(new JLabel(), constr);

    return pane;
  }

  protected void addWorkingDirectoryBrowseAction(final JPanel pane,
                                                 FixedSizeButton browseDirectoryButton,
                                                 JTextField tfCommandWorkingDirectory) {
    browseDirectoryButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
          PathChooserDialog chooser = FileChooserFactory.getInstance().createPathChooser(descriptor, myProject, pane);

          chooser.choose(null, new Consumer<List<VirtualFile>>() {
            @Override
            public void consume(List<VirtualFile> files) {
              VirtualFile file = !files.isEmpty() ? files.get(0) : null;
              if (file != null) {
                myTfCommandWorkingDirectory.setText(file.getPresentableUrl());
              }
            }
          });
        }
      }
    );
  }

  protected void addCommandBrowseAction(final JPanel pane, FixedSizeButton browseCommandButton, JTextField tfCommand) {
    browseCommandButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor();
          PathChooserDialog chooser = FileChooserFactory.getInstance().createPathChooser(descriptor, myProject, pane);
          chooser.choose(null, new Consumer<List<VirtualFile>>() {
            @Override
            public void consume(List<VirtualFile> files) {
              VirtualFile file = !files.isEmpty() ? files.get(0) : null;
              if (file != null) {
                myTfCommand.setText(file.getPresentableUrl());
                String workingDirectory = myTfCommandWorkingDirectory.getText();
                if (workingDirectory == null || workingDirectory.isEmpty()) {
                  VirtualFile parent = file.getParent();
                  if (parent != null && parent.isDirectory()) {
                    myTfCommandWorkingDirectory.setText(parent.getPresentableUrl());
                  }
                }
              }
            }
          });
        }
      }
    );
  }

  private class InsertMacroActionListener implements ActionListener {
    private final JTextField myTextField;

    public InsertMacroActionListener(JTextField textField) {
      myTextField = textField;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      MacrosDialog dialog = new MacrosDialog(myProject);
      if (dialog.showAndGet() && dialog.getSelectedMacro() != null) {
        String macro = dialog.getSelectedMacro().getName();
        int position = myTextField.getCaretPosition();
        try {
          myTextField.getDocument().insertString(position, "$" + macro + "$", null);
          myTextField.setCaretPosition(position + macro.length() + 2);
        }
        catch (BadLocationException ignored) {
        }
        ;
      }
      IdeFocusManager.findInstance().requestFocus(myTextField, true);
    }
  }

  private void addListeners() {
    myOutputFiltersButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        OutputFiltersDialog dialog = new OutputFiltersDialog(myOutputFiltersButton, getData().getOutputFilters());
        if (dialog.showAndGet()) {
          myOutputFilters = dialog.getData();
        }
      }
    });
    myInsertCommandMacroButton.addActionListener(new InsertMacroActionListener(myTfCommand));
    myInsertParametersMacroButton.addActionListener(new InsertMacroActionListener(myParametersField));
    myInsertWorkingDirectoryMacroButton.addActionListener(new InsertMacroActionListener(myTfCommandWorkingDirectory));

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        handleOKButton();
      }
    });

    myUseConsoleCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myShowConsoleOnStdOutCheckbox.setVisible(myUseConsoleCheckbox.isSelected());
        myShowConsoleOnStdErrCheckbox.setVisible(myUseConsoleCheckbox.isSelected());
      }
    });
  }

  private void handleOKButton() {
    setOKActionEnabled(!myNameField.getText().trim().isEmpty());
  }

  public Tool getData() {
    Tool tool = createTool();

    tool.setName(convertString(myNameField.getText()));
    tool.setDescription(convertString(myDescriptionField.getText()));
    Object selectedItem = myGroupCombo.getSelectedItem();
    tool.setGroup(StringUtil.notNullize(selectedItem != null ? convertString(selectedItem.toString()) : ""));
    tool.setShownInMainMenu(myShowInMainMenuCheckbox.isSelected());
    tool.setShownInEditor(myShowInEditorCheckbox.isSelected());
    tool.setShownInProjectViews(myShowInProjectTreeCheckbox.isSelected());
    tool.setShownInSearchResultsPopup(myShowInSearchResultsPopupCheckbox.isSelected());
    tool.setUseConsole(myUseConsoleCheckbox.isSelected());
    tool.setShowConsoleOnStdOut(myShowConsoleOnStdOutCheckbox.isSelected());
    tool.setShowConsoleOnStdErr(myShowConsoleOnStdErrCheckbox.isSelected());
    tool.setFilesSynchronizedAfterRun(mySynchronizedAfterRunCheckbox.isSelected());
    tool.setEnabled(myEnabled);

    tool.setWorkingDirectory(toSystemIndependentFormat(myTfCommandWorkingDirectory.getText()));
    tool.setProgram(convertString(myTfCommand.getText()));
    tool.setParameters(convertString(myParametersField.getText()));

    tool.setOutputFilters(myOutputFilters);

    return tool;
  }

  protected Tool createTool() {
    return new Tool();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.tools.ToolEditorDialog";
  }

  /**
   * Initialize controls
   */
  protected void setData(Tool tool, String[] existingGroups) {
    myNameField.setText(tool.getName());
    myDescriptionField.setText(tool.getDescription());
    if (myGroupCombo.getItemCount() > 0) {
      myGroupCombo.removeAllItems();
    }
    for (int i = 0; i < existingGroups.length; i++) {
      if (existingGroups[i] != null) {
        myGroupCombo.addItem(existingGroups[i]);
      }
    }
    myGroupCombo.setSelectedItem(tool.getGroup());
    myShowInMainMenuCheckbox.setSelected(tool.isShownInMainMenu());
    myShowInEditorCheckbox.setSelected(tool.isShownInEditor());
    myShowInProjectTreeCheckbox.setSelected(tool.isShownInProjectViews());
    myShowInSearchResultsPopupCheckbox.setSelected(tool.isShownInSearchResultsPopup());
    myUseConsoleCheckbox.setSelected(tool.isUseConsole());
    myShowConsoleOnStdOutCheckbox.setSelected(tool.isShowConsoleOnStdOut());
    myShowConsoleOnStdErrCheckbox.setSelected(tool.isShowConsoleOnStdErr());
    mySynchronizedAfterRunCheckbox.setSelected(tool.synchronizeAfterExecution());
    myEnabled = tool.isEnabled();
    myTfCommandWorkingDirectory.setText(toCurrentSystemFormat(tool.getWorkingDirectory()));
    myTfCommand.setText(tool.getProgram());
    myParametersField.setText(tool.getParameters());
    myOutputFilters = tool.getOutputFilters();
    mySimpleProgramPanel.setVisible(true);
    handleOKButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private JPanel getShowInPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    panel.setBorder(IdeBorderFactory.createTitledBorder(ToolsBundle.message("tools.menu.group"), true));
    panel.add(myShowInMainMenuCheckbox);
    panel.add(myShowInEditorCheckbox);
    panel.add(myShowInProjectTreeCheckbox);
    panel.add(myShowInSearchResultsPopupCheckbox);
    return panel;
  }

  private JPanel getOptionsPanel() {
    JPanel panel = new JPanel(new MigLayout("fill, gap 10"));
    panel.setBorder(IdeBorderFactory.createTitledBorder(ToolsBundle.message("tools.options.group"), true));
    panel.add(mySynchronizedAfterRunCheckbox);
    panel.add(myUseConsoleCheckbox);
    panel.add(myOutputFiltersButton, "ax right, wrap");
    panel.add(myShowConsoleOnStdOutCheckbox);
    panel.add(myShowConsoleOnStdErrCheckbox, "spanx 2");
    return panel;
  }

  private String convertString(String s) {
    if (s != null && s.trim().isEmpty()) return null;
    return s;
  }

  private String toSystemIndependentFormat(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    return s.replace(File.separatorChar, '/');
  }

  private String toCurrentSystemFormat(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    return s.replace('/', File.separatorChar);
  }

  public Project getProject() {
    return myProject;
  }
}
