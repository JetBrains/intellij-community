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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

public class ProjectNameWithTypeStep extends ProjectNameStep {
  private JEditorPane myModuleDescriptionPane;
  private JList myTypesList;
  private JCheckBox myCreateModuleCb;
  private JPanel myModulePanel;
  private JPanel myInternalPanel;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;


  public ProjectNameWithTypeStep(final WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    super(wizardContext, sequence, mode);
    myAdditionalContentPanel.add(myModulePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myCreateModuleCb.setVisible(myWizardContext.isCreatingNewProject());
    myCreateModuleCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        UIUtil.setEnabled(myInternalPanel, myCreateModuleCb.isSelected(), true);
        fireStateChanged();
      }
    });
    myCreateModuleCb.setSelected(true);
    if (!myWizardContext.isCreatingNewProject()){
      myInternalPanel.setBorder(null);
    }
    myModuleDescriptionPane.setContentType(UIUtil.HTML_MIME);
    myModuleDescriptionPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          try {
            BrowserUtil.launchBrowser(e.getURL().toString());
          }
          catch (IllegalThreadStateException ex) {
            // it's nnot a problem
          }
        }
      }
    });
    myModuleDescriptionPane.setEditable(false);

    final DefaultListModel defaultListModel = new DefaultListModel();
    for (ModuleBuilder builder : ModuleBuilder.getAllBuilders()) {
      defaultListModel.addElement(builder);
    }
    myTypesList.setModel(defaultListModel);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ModuleBuilder builder = (ModuleBuilder)value;
        setIcon(builder.getBigIcon());
        setDisabledIcon(builder.getBigIcon());
        setText(builder.getPresentableName());
        return rendererComponent;
      }
    });
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }

        final ModuleBuilder typeSelected = (ModuleBuilder)myTypesList.getSelectedValue();

        final StringBuilder sb = new StringBuilder("<html><body><font face=\"Verdana\" ");
        sb.append(SystemInfo.isMac ? "" : "size=\"-1\"").append('>');
        sb.append(typeSelected.getDescription()).append("</font></body></html>");

        myModuleDescriptionPane.setText(sb.toString());

        fireStateChanged();
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            myTypesList.requestFocusInWindow();
          }
        });
      }
    });
    myTypesList.setSelectedIndex(0);
    myTypesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          myWizardContext.requestNextStep();
        }
      }
    });

    final Dimension preferredSize = calcTypeListPreferredSize(ModuleBuilder.getAllBuilders());
    final JBScrollPane pane = IJSwingUtilities.findParentOfType(myTypesList, JBScrollPane.class);
    pane.setPreferredSize(preferredSize);
    pane.setMinimumSize(preferredSize);

    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(myNamePathComponent.getNameValue());
        }
      }
    });

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"), ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(myNamePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
        String path = getDefaultBaseDir(wizardContext);
        if (path.length() > 0 && !Comparing.strEqual(myModuleName.getText().trim(), myNamePathComponent.getNameValue())) {
          path += "/" + myModuleName.getText();
        }
        if (!myContentRootChangedByUser) {
          final boolean f = myModuleNameChangedByUser;
          myModuleNameChangedByUser = true;
          setModuleContentRoot(path);
          myModuleNameChangedByUser = f;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(path);
        }
      }
    });
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myContentRootDocListenerEnabled) {
          myContentRootChangedByUser = true;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myModuleContentRoot.getText());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(myModuleContentRoot.getText());
          final int idx = path.lastIndexOf("/");

          boolean f = myContentRootChangedByUser;
          myContentRootChangedByUser = true;

          boolean i = myImlLocationChangedByUser;
          myImlLocationChangedByUser = true;

          setModuleName(idx >= 0 ? path.substring(idx + 1) : "");

          myContentRootChangedByUser = f;
          myImlLocationChangedByUser = i;
        }
      }
    });

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"), ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myNamePathComponent.getPath());
        }
      }
    });
    if (wizardContext.isCreatingNewProject()) {
      setModuleName(myNamePathComponent.getNameValue());
      setModuleContentRoot(myNamePathComponent.getPath());
      setImlFileLocation(myNamePathComponent.getPath());
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDir.getPath(), "untitled", "");
        setModuleName(moduleName);
        setModuleContentRoot(baseDir.getPath() + "/" + moduleName);
        setImlFileLocation(baseDir.getPath() + "/" + moduleName);
        myModuleName.setSelectionStart(0);
        myModuleName.setSelectionEnd(moduleName.length());
      }
    }
  }

  private Dimension calcTypeListPreferredSize(final List<ModuleBuilder> allModuleTypes) {
    int width = 0;
    int height = 0;
    final FontMetrics fontMetrics = myTypesList.getFontMetrics(myTypesList.getFont());
    final int fontHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    for (final ModuleBuilder type : allModuleTypes) {
      final Icon icon = type.getBigIcon();
      final int iconHeight = icon != null ? icon.getIconHeight() : 0;
      final int iconWidth = icon != null ? icon.getIconWidth() : 0;
      height += Math.max(iconHeight, fontHeight) + 6;
      width = Math.max(width, iconWidth + fontMetrics.stringWidth(type.getPresentableName()) + 10);
    }
    return new Dimension(width, height);
  }

  private String getDefaultBaseDir(WizardContext wizardContext) {
    if (wizardContext.isCreatingNewProject()) {
      return myNamePathComponent.getPath();
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myWizardContext.isCreatingNewProject() ? super.getPreferredFocusedComponent() : myModuleName;
  }

  private void setImlFileLocation(final String path) {
    myImlLocationDocListenerEnabled = false;
    myModuleFileLocation.setText(FileUtil.toSystemDependentName(path));
    myImlLocationDocListenerEnabled = true;
  }

  private void setModuleContentRoot(final String path) {
    myContentRootDocListenerEnabled = false;
    myModuleContentRoot.setText(FileUtil.toSystemDependentName(path));
    myContentRootDocListenerEnabled = true;
  }

  private void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  public void updateStep() {
    super.updateStep();
    if (myCreateModuleCb.isSelected()) {
      mySequence.setType(getSelectedBuilderId());
    } else {
      mySequence.setType(null);
    }
  }

  public void updateDataModel() {
    if (myCreateModuleCb.isSelected()) {
      mySequence.setType(getSelectedBuilderId());
      super.updateDataModel();
      final ModuleBuilder builder = (ModuleBuilder)myMode.getModuleBuilder();
      assert builder != null;
      builder.setName(myModuleName.getText());
      builder.setModuleFilePath(FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + myModuleName.getText() + ModuleFileType.DOT_DEFAULT_EXTENSION);
      ((SourcePathsBuilder)builder).setContentEntryPath(FileUtil.toSystemIndependentName(myModuleContentRoot.getText()));
    } else {
      mySequence.setType(null);
      super.updateDataModel();
    }
  }

  private String getSelectedBuilderId() {
    return ((ModuleBuilder)myTypesList.getSelectedValue()).getBuilderId();
  }

  public boolean validate() throws ConfigurationException {
    final String moduleName = myModuleName.getText().trim();
    if (myCreateModuleCb.isSelected() || !myWizardContext.isCreatingNewProject()) {
      final String moduleFileDirectory = myModuleFileLocation.getText();
      if (moduleFileDirectory.length() == 0) {
        throw new ConfigurationException("Enter module file location");
      }
      if (moduleName.length() == 0) {
        throw new ConfigurationException("Enter a module name");
      }

      if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory,
                                                        myImlLocationChangedByUser)) {
        return false;
      }
      if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(),
                                                        myContentRootChangedByUser)) {
        return false;
      }

      File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      if (moduleFile.exists()) {
        int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(), IdeBundle.message("project.new.wizard.module.identification")),
                                              IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
        if (answer != 0) {
          return false;
        }
      }
    }
    if (!myWizardContext.isCreatingNewProject()) {
      final Module module;
      final ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(myWizardContext.getProject());
      if (fromConfigurable != null) {
        module = fromConfigurable.getModulesConfig().getModule(moduleName);
      }
      else {
        module = ModuleManager.getInstance(myWizardContext.getProject()).findModuleByName(moduleName);
      }
      if (module != null) {
        throw new ConfigurationException("Module \'" + moduleName + "\' already exist in project. Please, specify another name.");
      }
    }
    return !myWizardContext.isCreatingNewProject() || super.validate();
  }

  public void disposeUIResources() {
    super.disposeUIResources();
  }

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch";
  }
}